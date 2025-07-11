import { useAtomValue } from "jotai";
import { ScopeProvider } from "bunshi/react";
import { selectAtom, useAtomCallback } from "jotai/utils";
import uniq from "lodash/uniq";
import {
  SyntheticEvent,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

import { clsx, Box, DndProvider, Input, Link, useDrag } from "@axelor/ui";
import { GridColumn, GridRow, GridRowProps } from "@axelor/ui/grid";
import { MaterialIcon } from "@axelor/ui/icons/material-icon";

import { dialogs } from "@/components/dialogs";
import { PageText } from "@/components/page-text";
import { useDataStore } from "@/hooks/use-data-store";
import { usePerms } from "@/hooks/use-perms";
import { useEditor } from "@/hooks/use-relation";
import { useResponsive } from "@/hooks/use-responsive";
import { useRoute } from "@/hooks/use-route";
import { useSession } from "@/hooks/use-session";
import { useTabs } from "@/hooks/use-tabs";
import { SearchOptions } from "@/services/client/data";
import { DataRecord } from "@/services/client/data.types";
import { i18n } from "@/services/client/i18n";
import { GridView } from "@/services/client/meta.types";
import { DEFAULT_PAGE_SIZE } from "@/utils/app-settings.ts";
import { sanitize } from "@/utils/sanitize.ts";
import { AdvanceSearch } from "@/view-containers/advance-search";
import {
  usePopupHandlerAtom,
  useSetPopupHandlers,
} from "@/view-containers/view-popup/handler";
import { ViewToolBar } from "@/view-containers/view-toolbar";
import {
  useViewAction,
  useViewContext,
  useViewTab,
  useViewTabRefresh,
} from "@/view-containers/views/scope";

import { legacyClassNames } from "@/styles/legacy";
import { Grid as GridComponent } from "../grid/builder";
import { useGridState } from "../grid/builder/utils";
import gridRowStyles from "../grid/renderers/row/row.module.css";
import { ViewProps } from "../types";
import {
  DMS_NODE_TYPE,
  DmsDetails,
  DmsOverlay,
  DmsTree,
  DmsUpload,
  TreeRecord,
} from "./builder";
import { DMSCustomDragLayer } from "./builder/dms-drag-layer";
import { DMSGridScope } from "./builder/handler";
import { Uploader } from "./builder/scope";
import {
  CONTENT_TYPE,
  downloadAsBatch,
  prepareCustomView,
  toStrongText,
} from "./builder/utils";
import { DataStore } from "@/services/client/data-store";
import { useAsyncEffect } from "@/hooks/use-async-effect";

import styles from "./dms.module.scss";

const UNDEFINED_ID = -1;

const promptInput = async (
  title: string,
  inputValue: string = "",
  yesTitle?: string,
) => {
  const confirmed = await dialogs.confirm({
    title,
    content: (
      <Box d="flex" w={100}>
        <Input
          type="text"
          autoFocus={true}
          defaultValue={inputValue}
          onChange={(e) => {
            inputValue = e.target.value;
          }}
        />
      </Box>
    ),
    yesTitle: yesTitle ?? i18n.get("Create"),
  });
  return confirmed && inputValue;
};

export function Dms(props: ViewProps<GridView>) {
  const { meta, dataStore, searchAtom } = props;
  const { view, fields } = meta;
  const { action, popup, popupOptions } = useViewTab();
  const { data: session } = useSession();
  const { hasButton } = usePerms(meta.view, meta.perms);
  const { open: openTab } = useTabs();
  const { navigate } = useRoute();
  const showEditor = useEditor();
  const popupHandler = usePopupHandlerAtom();
  const setPopupHandlers = useSetPopupHandlers();

  const getViewContext = useViewContext();
  const viewAction = useViewAction();

  const popupRecord = action.params?.["_popup-record"];

  const ROOT = useMemo<TreeRecord>(
    () =>
      popupRecord ?? {
        id: null,
        fileName: i18n.get("DMS.Home"),
      },
    [popupRecord],
  );
  const [state, setState] = useGridState();
  const [root, setRoot] = useState<TreeRecord>(ROOT);
  const [detailsId, setDetailsId] = useState<TreeRecord["id"]>(null);
  const [showDetails, setDetailsPopup] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const contentRef = useRef<HTMLDivElement | null>(null);

  // tree state
  const [treeRecords, setTreeRecords] = useState<TreeRecord[]>([root]);
  const [expanded, setExpanded] = useState<TreeRecord["id"][]>([root.id]);
  const [selected, setSelected] = useState<TreeRecord["id"]>(root.id);
  const [showTree, setShowTree] = useState<boolean | undefined>(undefined);

  const { orderBy, rows, selectedRows } = state;
  const uploadSize = session?.data?.upload?.maxSize ?? 0;
  const uploader = useMemo(() => new Uploader(), []);
  const { relatedId, relatedModel } = popupRecord || {};

  /**
   * Return the first selected node in the DMSTree
   * @returns {DataRecord|null} - record if found, otherwise `null`.
   */
  const getSelectedNode = useCallback(() => {
    const selectedTreeNode = treeRecords.find((r) => r.id === selected);
    return selectedTreeNode ? (selectedTreeNode as DataRecord) : null;
  }, [treeRecords, selected]);

  const allFields = useAtomValue(
    useMemo(() => selectAtom(searchAtom!, (s) => s.fields), [searchAtom]),
  );

  /**
   * Get the first selected dms record in dms grid list
   * @returns {DataRecord|null} - record if found, otherwise `null`.
   */
  const getSelectedDocument = useCallback(() => {
    const selectedDocument = rows[selectedRows?.[0] ?? -1]?.record;
    return selectedDocument ? (selectedDocument as DataRecord) : null;
  }, [rows, selectedRows]);

  /**
   * Get the selected dms records in dms grid list
   * @returns {DataRecord[]|null} - records if found, otherwise `null`.
   */
  const getSelectedDocuments = useCallback(() => {
    const records = selectedRows
      ?.map?.((ind) => rows[ind]?.record)
      ?.filter(Boolean);
    return records?.length ? (records as DataRecord[]) : null;
  }, [rows, selectedRows]);

  /**
   * Get the first selected dms record either in the dms grid list or the selected node in the DMSTree
   * @returns {DataRecord|null} - record if found, otherwise `null`.
   */
  const getFirstSelected = useCallback(() => {
    return getSelectedDocument() || getSelectedNode();
  }, [getSelectedDocument, getSelectedNode]);

  /**
   * Get the selected dms record either in the dms grid list or the selected node in the DMSTree
   * @returns {DataRecord[]|null} - records if found, otherwise `null`.
   */
  const getSelected = useCallback(() => {
    const docs = getSelectedDocuments();
    const node = getSelectedNode();
    if (!docs && !node) return null;
    return docs! || [node!];
  }, [getSelectedDocuments, getSelectedNode]);

  const supportsSpreadsheet = session?.features?.dmsSpreadsheet;

  const openDMSFile = useAtomCallback(
    useCallback(
      (get, set, record: TreeRecord) => {
        const customViewTypes = [
          ...(supportsSpreadsheet ? [CONTENT_TYPE.SPREADSHEET] : []),
          CONTENT_TYPE.HTML,
        ];
        if (customViewTypes.includes(record.contentType)) {
          // open HTML/spreadsheet view
          openTab(prepareCustomView(view, record));
        } else {
          navigate(`/ds/dms.file/edit/${record.id}`);
        }
        if (popup) {
          const popupState = get(popupHandler);
          popupState.close?.();
        }
      },
      [supportsSpreadsheet, popup, openTab, view, navigate, popupHandler],
    ),
  );

  const setRootIfNeeded = useCallback(
    ({ parent }: TreeRecord) => {
      if (parent && root.id === UNDEFINED_ID) {
        setRoot((record) => ({
          ...record,
          ...parent,
        }));
        setTreeRecords((records) =>
          records.map((rec) =>
            rec.id === root.id ? { ...rec, ...parent } : rec,
          ),
        );
        setSelected((id) => (id === root.id ? parent.id : id));
        setExpanded((ids) =>
          ids.map((id) => (id === root.id ? parent.id : id)),
        );
        return true;
      }
    },
    [root],
  );

  const selectNode = useCallback((record: TreeRecord) => {
    setSelected(record.id);
    setDetailsPopup(false);
    setDetailsId(null);
  }, []);

  const shouldSearch = useRef(true);

  const onTreeSearch = useCallback(() => {
    const treeDS = new DataStore(dataStore.model);
    const wheres = [
      ...(action.domain ? [action.domain] : []),
      "self.isDirectory = true",
      ...(popupRecord?.id
        ? [
            `self.id != ${popupRecord.id}`,
            `self.parent.id = ${popupRecord.id}`,
            ...(popupRecord.relatedModel && [
              `self.relatedModel = '${popupRecord.relatedModel}'`,
            ]),
            ...(popupRecord.relatedId && [
              `self.relatedId = '${popupRecord.relatedId}'`,
            ]),
          ]
        : ["self.parent is NULL"]),
    ];
    treeDS
      .search({
        fields: ["fileName", "parent.id"],
        filter: {
          _domain: wheres.join(" AND "),
          _domainContext: {
            _populate: false,
          },
        },
        offset: 0,
        limit: -1,
        translate: true,
      })
      .then((result) => {
        const { records } = result;
        setTreeRecords([ROOT, ...records]);
      });
  }, [ROOT, action.domain, dataStore, popupRecord]);

  const onSearch = useAtomCallback(
    useCallback(
      (get, set, options: Partial<SearchOptions> = {}) => {
        if (!shouldSearch.current) {
          shouldSearch.current = true;
          return Promise.resolve(undefined);
        }

        const { query = {}, searchText = "" } = searchAtom
          ? get(searchAtom)
          : {};

        let domain: string;
        if (searchText?.trim()) {
          domain = `self.isDirectory = FALSE${
            root.id ? ` AND self.parent.id = ${root.id}` : ""
          }`;

          if (selected !== root.id) {
            // Prevent duplicated search on node change
            shouldSearch.current = false;
            selectNode(root);
          }
        } else {
          domain = selected
            ? `self.parent.id = ${selected}`
            : "self.parent IS NULL";
        }

        const sortBy = orderBy?.map(
          (column) => `${column.order === "desc" ? "-" : ""}${column.name}`,
        );

        return dataStore
          .search({
            sortBy,
            filter: {
              ...query,
              _domain: `${
                action.domain ? `${action.domain} AND ` : ""
              }${domain}`,
            },
            // reset offset is not provided (ie, not using PageText)
            offset: 0,
            translate: true,
            ...options,
            ...(options.fields && {
              fields: uniq([
                ...options.fields,
                "isDirectory",
                "parent",
                "parent.id",
                "relatedModel",
                "relatedId",
                "metaFile.id",
              ]),
            }),
          })
          .then((result) => {
            const { records } = result;
            const dirs = records.filter((r) => r.isDirectory);

            setTreeRecords((_records) => {
              const recIds = _records.map((r) => r.id);
              const newRecords = dirs.filter((r) => !recIds.includes(r.id));
              return [
                ..._records.map((rec) => {
                  const dir = dirs.find((d) => d.id === rec.id);
                  return dir ? { ...rec, ...dir } : rec;
                }),
                ...newRecords,
              ];
            });

            return result;
          });
      },
      [
        searchAtom,
        orderBy,
        dataStore,
        action.domain,
        selectNode,
        root,
        selected,
      ],
    ),
  );

  const onNew = useCallback(
    async (title: string, inputValue?: string, data?: TreeRecord) => {
      const node = getSelectedNode();

      const regex = new RegExp(`^${inputValue}.?(\\(([0-9]+)\\))?$`);
      let count = 0;

      const children = data?.isDirectory
        ? treeRecords.filter((r) => r["parent.id"] === selected)
        : dataStore.records;

      children.forEach((rec) => {
        const match = regex.exec(rec.fileName);

        if (match) {
          count = Math.max(count, (+match[2] ? +match[2] : 1) + 1);
        }
      });

      if (count > 0) {
        inputValue = `${inputValue} (${count})`;
      }

      const input = await promptInput(title, inputValue);

      if (input) {
        const record = await dataStore.save({
          relatedId,
          relatedModel,
          fileName: input,
          ...data,
          ...(node?.id &&
            node.id > 0 && {
              parent: {
                id: node.id,
              },
            }),
        });
        if (record) {
          if (record.isDirectory) {
            setTreeRecords((list) => [
              ...list,
              { ...record, "parent.id": record.parent && record.parent.id },
            ]);
          }
          const rootChanged = setRootIfNeeded(record);
          if (!rootChanged) {
            onSearch();
          }
        }
        return record;
      }
    },
    [
      treeRecords,
      selected,
      relatedId,
      relatedModel,
      dataStore,
      setRootIfNeeded,
      getSelectedNode,
      onSearch,
    ],
  );

  const onFolderNew = useCallback(async () => {
    return onNew(i18n.get("Create folder"), i18n.get("New Folder"), {
      isDirectory: true,
    });
  }, [onNew]);

  const onDocumentNew = useCallback(async () => {
    const record = await onNew(
      i18n.get("Create document"),
      i18n.get("New Document"),
      {
        isDirectory: false,
        contentType: CONTENT_TYPE.HTML,
      },
    );
    if (record) openDMSFile(record);
  }, [onNew, openDMSFile]);

  const onSpreadsheetNew = useCallback(async () => {
    const record = await onNew(
      i18n.get("Create spreadsheet"),
      i18n.get("New Spreadsheet"),
      {
        isDirectory: false,
        contentType: CONTENT_TYPE.SPREADSHEET,
      },
    );
    if (record) openDMSFile(record);
  }, [onNew, openDMSFile]);

  const onDocumentRename = useCallback(async () => {
    const record = getFirstSelected();

    if (!record) return;

    const input = await promptInput(
      i18n.get("Information"),
      record.fileName,
      i18n.get("Save"),
    );

    if (input) {
      const updated = await dataStore.save({
        id: record.id,
        version: record.version,
        fileName: input,
      });
      if (updated) {
        const { fileName, version } = updated;
        setTreeRecords((records) =>
          records.map((r) =>
            r.id === updated.id ? { ...r, fileName, version } : r,
          ),
        );
      }
    }
  }, [getFirstSelected, dataStore]);

  const onDocumentSave = useCallback(
    (record: TreeRecord) => dataStore.save(record),
    [dataStore],
  );

  const onDocumentDownload = useCallback(async () => {
    const records = getSelected();
    if (!records) return;

    downloadAsBatch(records.map((_rec) => _rec.id!));
  }, [getSelected]);

  const onDocumentPermissions = useCallback(() => {
    const doc = getSelectedDocument();
    if (doc) {
      // TODO: permissions dialog
      showEditor({
        title: i18n.get("Permissions {0}", doc.fileName),
        model: view.model!,
        viewName: "dms-file-permission-form",
        record: doc,
        context: getViewContext(true),
        canAttach: false,
        readonly: false,
        onSelect: () => {},
      });
    }
  }, [view, showEditor, getViewContext, getSelectedDocument]);

  const onDocumentAttachedTo = useCallback(() => {
    const doc = getSelectedDocument();
    if (doc && doc.relatedId > 0 && doc.relatedModel) {
      navigate(`/ds/form::${doc.relatedModel}/edit/${doc.relatedId}`);
    }
  }, [navigate, getSelectedDocument]);

  const onDocumentDelete = useCallback(async () => {
    const records = getSelected();
    if (!records) return;

    const confirmed = await dialogs.confirm({
      content:
        records.length > 1 ? (
          i18n.get(
            "Are you sure you want to delete the {0} selected documents?",
            records.length,
          )
        ) : (
          <Box
            dangerouslySetInnerHTML={{
              __html: i18n.get(
                "Are you sure you want to delete {0}?",
                toStrongText(sanitize(records[0].fileName)),
              ),
            }}
          />
        ),
    });

    if (confirmed) {
      const result = await dataStore.delete(
        records.map((_rec) => ({ id: _rec.id!, version: _rec.version! })),
      );
      if (result) {
        const dirIds = records.filter((r) => r.isDirectory).map((r) => r.id);

        // remove directories from tree
        if (dirIds.length > 0) {
          setTreeRecords((_records) =>
            _records.filter((r) => !dirIds.includes(r.id)),
          );
        }

        // set to root when selected node is deleted
        setSelected((_selected) =>
          dirIds.includes(_selected) ? root.id : _selected,
        );
      }
    }
  }, [getSelected, dataStore, root.id]);

  const handleUpload = useCallback(
    async (files: FileList | null) => {
      if (!files) return;

      for (let i = 0; i < files.length; i++) {
        const file = files?.[i];
        if (file && uploadSize > 0 && file.size > 1048576 * uploadSize) {
          return dialogs.info({
            content: i18n.get(
              "You are not allowed to upload a file bigger than {0} MB.",
              uploadSize,
            ),
          });
        }
      }

      for (let i = 0; i < files.length; i++) {
        uploader.queue({
          file: files[i],
        });
      }

      await uploader.process();
    },
    [uploadSize, uploader],
  );

  const handleNodeDrop = useCallback(
    async (node: TreeRecord, _records: DataRecord[]) => {
      // skip drop if node is included in selected grid row
      if (_records.find((r) => r.id === node.id)) return;

      const records = await dataStore.save(
        _records.map(({ id, version }) => ({
          id,
          version,
          parent: node.id ? { id: node.id } : null,
        })),
      );
      if (records) {
        (records as DataRecord[]).forEach((record) => {
          if (record.isDirectory) {
            setTreeRecords((list) => {
              const exist = list.some((r) => r.id === record.id);
              if (exist) {
                return list.map((r) =>
                  r.id === record.id
                    ? {
                        ...r,
                        "parent.id": node.id,
                      }
                    : r,
                );
              }
              return [...list, { ...record, "parent.id": node.id }];
            });
          }
        });
        onSearch();
      }
    },
    [dataStore, onSearch],
  );

  const clearSearch = useAtomCallback(
    useCallback(
      (get, set) => {
        if (searchAtom) {
          set(searchAtom, (prev) => {
            return {
              ...prev,
              query: {
                criteria: [],
              },
              searchText: undefined,
            };
          });
        }
      },
      [searchAtom],
    ),
  );

  const handleNodeSelect = useCallback(
    (record: TreeRecord) => {
      selectNode(record);
      clearSearch();
    },
    [selectNode, clearSearch],
  );

  const handleNodeExpand = useCallback((record: TreeRecord) => {
    setExpanded((list) =>
      list.includes(record.id)
        ? list.filter((id) => id !== record.id)
        : [...list, record.id],
    );
  }, []);

  const handleDocumentOpen = useCallback(
    (e: SyntheticEvent, row: GridRow) => {
      if (row?.record?.isDirectory) {
        handleNodeSelect(row.record);
      } else if (row?.record?.id) {
        openDMSFile(row?.record);
      }
    },
    [handleNodeSelect, openDMSFile],
  );

  const handleDetailsPopupClose = useCallback(() => {
    setDetailsPopup(false);
  }, []);

  const handleGridCellClick = useCallback(
    (
      e: React.SyntheticEvent,
      col: GridColumn,
      colIndex: number,
      row: GridRow,
    ) => {
      const record = row?.record;
      if (col?.name === "typeIcon" && record.isDirectory) {
        handleDocumentOpen(e, row);
      } else if (col?.name === "downloadIcon") {
        if (row?.record) downloadAsBatch([row.record.id]);
      } else if (col?.name === "detailsIcon") {
        setDetailsPopup(true);
        setDetailsId(record.id);
      } else if (col?.name === "typeIcon") {
        handleDocumentOpen(e, row);
      }

      if (col?.name !== "detailsIcon") {
        setDetailsPopup(false);
        setDetailsId(null);
      }
    },
    [handleDocumentOpen],
  );

  const showToolbar = popupOptions?.showToolbar !== false;

  const {
    offset = 0,
    limit = DEFAULT_PAGE_SIZE,
    totalCount = 0,
  } = dataStore.page;
  const canPrev = offset > 0;
  const canNext = offset + limit < totalCount;
  const records = useDataStore(dataStore, (ds) => ds.records);
  const breadcrumbs = useMemo(() => {
    function collect(parentId: TreeRecord["id"]): TreeRecord[] {
      const item = treeRecords.find((r) => r.id === parentId);
      return item ? collect(item["parent.id"]).concat([item]) : [];
    }
    return collect(selected);
  }, [treeRecords, selected]);
  const detailRecord = useMemo(
    () => (detailsId ? records.find((r) => r.id === detailsId) : null),
    [records, detailsId],
  );

  const onTabRefresh = useCallback(() => {
    onTreeSearch();
    onSearch();
  }, [onSearch, onTreeSearch]);

  useEffect(() => {
    const parent = getSelectedNode();
    uploader.setSaveHandler(async (data: DataRecord) => {
      const record = await dataStore.save({
        relatedId,
        relatedModel,
        ...data,
        ...(parent?.id &&
          parent.id > 0 && {
            parent: {
              id: parent.id,
            },
          }),
      });
      if (record) setRootIfNeeded(record);
      return record;
    });
  }, [
    relatedId,
    relatedModel,
    uploader,
    dataStore,
    getSelectedNode,
    setRootIfNeeded,
  ]);

  useEffect(() => {
    if (!showDetails) setDetailsId(null);
  }, [showDetails]);

  useEffect(() => {
    if (popup) {
      setPopupHandlers({
        data: {
          selected: getSelectedDocuments(),
        },
      });
    }
  }, [popup, getSelectedDocuments, setPopupHandlers]);

  useAsyncEffect(async () => onTreeSearch(), [onTreeSearch]);

  // register tab:refresh
  useViewTabRefresh("grid", onTabRefresh);

  const size = useResponsive();

  const canShowTree = showTree ?? (size.xs || size.sm ? false : true);

  return (
    <DndProvider>
      <DmsOverlay
        className={clsx(styles.container, {
          [styles.popup]: popup,
        })}
        onUpload={handleUpload}
      >
        {showToolbar && (
          <ViewToolBar
            meta={meta}
            actions={[
              {
                key: "toggle",
                text: i18n.get("Toggle"),
                iconOnly: true,
                iconProps: {
                  icon: "menu",
                },
                onClick: () => setShowTree((show) => !show),
              },
              {
                key: "new",
                text: i18n.get("New"),
                hidden: !hasButton("new"),
                iconOnly: false,
                onClick: onFolderNew,
                items: [
                  {
                    key: "folder",
                    text: i18n.get("Folder"),
                    onClick: onFolderNew,
                  },
                  { key: "d1", divider: true },
                  {
                    key: "document",
                    text: i18n.get("Document"),
                    onClick: onDocumentNew,
                  },
                  {
                    key: "spreadsheet",
                    text: i18n.get("Spreadsheet"),
                    onClick: onSpreadsheetNew,
                    hidden: !supportsSpreadsheet,
                  },
                  { key: "d2", divider: true },
                  {
                    key: "file_upload",
                    text: i18n.get("File upload"),
                    onClick: () => fileInputRef.current?.click?.(),
                  },
                ],
              },
              {
                key: "more",
                text: i18n.get("More"),
                iconProps: {
                  icon: "more_vert",
                },
                disabled: selected === root.id && !selectedRows?.length,
                items: [
                  {
                    key: "rename",
                    text: i18n.get("Rename..."),
                    onClick: onDocumentRename,
                  },
                  {
                    key: "permissions",
                    text: i18n.get("Permissions..."),
                    onClick: onDocumentPermissions,
                    hidden: !selectedRows?.length,
                  },
                  {
                    key: "attached",
                    text: i18n.get("Attached to..."),
                    onClick: onDocumentAttachedTo,
                    hidden:
                      Boolean(viewAction.params?.popup) ||
                      !(
                        getSelectedDocument()?.relatedId > 0 &&
                        getSelectedDocument()?.relatedModel
                      ),
                  },
                  { key: "d1", divider: true },
                  {
                    key: "download",
                    text: i18n.get("Download"),
                    onClick: onDocumentDownload,
                  },
                  { key: "d2", divider: true },
                  {
                    key: "delete",
                    text: i18n.get("Delete..."),
                    onClick: onDocumentDelete,
                  },
                ],
              },
            ]}
            pagination={{
              canPrev,
              canNext,
              onPrev: () => onSearch({ offset: offset - limit }),
              onNext: () => onSearch({ offset: offset + limit }),
              text: () => <PageText dataStore={dataStore} />,
            }}
          >
            <Box d="flex" className={styles.toolbar}>
              {searchAtom && (
                <AdvanceSearch
                  stateAtom={searchAtom}
                  dataStore={dataStore}
                  items={view.items}
                  customSearch={view.customSearch}
                  freeSearch={view.freeSearch}
                  onSearch={onSearch}
                />
              )}
              <Box
                d="flex"
                flex={1}
                alignItems="center"
                className={styles.breadcrumbs}
              >
                <Breadcrumbs
                  root={root}
                  data={breadcrumbs}
                  selected={selected}
                  onSelect={handleNodeSelect}
                />
              </Box>
            </Box>
          </ViewToolBar>
        )}
        <ScopeProvider scope={DMSGridScope} value={{ getSelectedDocuments }}>
          <Box className={styles.content}>
            <Input
              type="file"
              multiple={true}
              ref={fileInputRef}
              d="none"
              onChange={(e) => handleUpload(e.target.files)}
            />
            <Box
              className={clsx(styles.tree, {
                [styles.hide]: !canShowTree,
              })}
            >
              <DmsTree
                root={root}
                data={treeRecords}
                expanded={expanded}
                selected={selected}
                onDrop={handleNodeDrop}
                onSelect={handleNodeSelect}
                onExpand={handleNodeExpand}
              />
            </Box>
            <Box className={styles.grid} ref={contentRef}>
              <GridComponent
                records={records}
                view={view}
                fields={fields}
                state={state}
                setState={setState}
                sortType={"live"}
                rowRenderer={DMSGridRow}
                onSearch={onSearch}
                onCellClick={handleGridCellClick}
                onRowDoubleClick={handleDocumentOpen}
              />
              <DmsDetails
                open={showDetails}
                fields={allFields}
                data={detailRecord}
                onView={openDMSFile}
                onSave={onDocumentSave}
                onClose={handleDetailsPopupClose}
              />
            </Box>
            <DMSCustomDragLayer />
          </Box>
        </ScopeProvider>
        <DmsUpload uploader={uploader} />
      </DmsOverlay>
    </DndProvider>
  );
}

const emptyImage = new Image();
emptyImage.src =
  "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==";

function DMSGridRow(props: GridRowProps) {
  const { selected, data, index } = props;
  const { children, style, className, onDoubleClick } =
    props as React.HTMLAttributes<HTMLDivElement>;
  const ref = useRef<HTMLDivElement>(null);
  const [, dragRef, dragPreviewRef] = useDrag({
    type: DMS_NODE_TYPE,
    item: { data: data, index, type: DMS_NODE_TYPE },
  });
  dragRef(ref);

  useEffect(() => {
    dragPreviewRef(emptyImage, { captureDraggingState: true });
  }, [dragPreviewRef]);

  return (
    <>
      <Box
        ref={ref}
        position="relative"
        {...{
          style,
          className: legacyClassNames(className, gridRowStyles.row, {
            [gridRowStyles.selected]: selected,
          }),
          onDoubleClick,
        }}
      >
        {children}
      </Box>
    </>
  );
}

const MAX_BREADCRUMBS = 3;

function Breadcrumbs({
  root,
  data: _data,
  selected,
  onSelect,
}: {
  root: TreeRecord;
  data: TreeRecord[];
  selected?: TreeRecord["id"];
  onSelect?: (data: TreeRecord) => void;
}) {
  const data = useMemo(() => {
    if (_data.length <= MAX_BREADCRUMBS) {
      return _data;
    }
    const [_root, ...rest] = _data;
    return [
      _root,
      { ...rest.slice(-MAX_BREADCRUMBS)[0], $displayName: "..." },
      ...rest.slice(1 - MAX_BREADCRUMBS),
    ];
  }, [_data]);

  return (
    <Box as="ul">
      {data.map((item, ind) => {
        function render() {
          return item.id === root.id ? (
            <MaterialIcon icon="home" fill />
          ) : (
            (item.$displayName ?? item.fileName)
          );
        }
        return (
          <Box key={ind} as="li">
            {selected === item.id && item.id ? (
              <Box as="span">{render()}</Box>
            ) : (
              <Link onClick={() => onSelect?.(item)} title={item.fileName}>
                {render()}
              </Link>
            )}
            {ind < data.length - 1 && (
              <Box as="span" color="secondary">
                <MaterialIcon icon="chevron_right" />
              </Box>
            )}
          </Box>
        );
      })}
    </Box>
  );
}
