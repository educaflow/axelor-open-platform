import { atom, useAtomValue, useSetAtom } from "jotai";
import { selectAtom, useAtomCallback } from "jotai/utils";
import { ChangeEvent, useCallback, useMemo, useRef } from "react";

import { Box, Button, ButtonGroup } from "@axelor/ui";

import { DataRecord } from "@/services/client/data.types";
import { download } from "@/utils/download";
import { MaterialIcon } from "@axelor/ui/icons/material-icon";

import { i18n } from "@/services/client/i18n";
import { FieldControl, FieldProps, FormAtom } from "../../builder";
import {
  META_FILE_MODEL,
  makeImageURL,
  validateFileSize,
} from "../image/utils";
import { useViewDirtyAtom } from "@/view-containers/views/scope";
import { formDirtyUpdater } from "../../builder/atoms";

function useFormFieldSetter(formAtom: FormAtom, fieldName: string) {
  return useSetAtom(
    useMemo(
      () =>
        atom(null, (get, set, value: any) => {
          set(formAtom, ({ record, ...rest }) => ({
            ...rest,
            record: { ...record, [fieldName]: value },
          }));
        }),
      [formAtom, fieldName],
    ),
  );
}

function readFileAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result;
      if (typeof result === "string") {
        const base64 = result.split(",")[1] || result;
        resolve(base64);
      } else {
        reject("Unexpected result from FileReader");
      }
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function base64ToBlob(base64: string, type = "application/octet-stream") {
  const byteCharacters = atob(base64);
  const byteNumbers = new Array(byteCharacters.length);
  for (let i = 0; i < byteCharacters.length; i++) {
    byteNumbers[i] = byteCharacters.charCodeAt(i);
  }
  const byteArray = new Uint8Array(byteNumbers);
  return new Blob([byteArray], { type });
}

export function BinaryEduFlow(props: FieldProps<string | DataRecord | undefined | null>) {
  const { schema, readonly, valueAtom, formAtom, widgetAtom } = props;
  const { name, accept } = schema;
  const inputRef = useRef<HTMLInputElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const value = useAtomValue(valueAtom);

  //const setValue = useSetAtom(valueAtom);
  const setValue = useAtomCallback(
    useCallback((get, set, val: string | null) => {
      set(valueAtom, val);
    }, [valueAtom])
  );  
  const dirtyAtom = useViewDirtyAtom();
  const parentId = useAtomValue(
    useMemo(() => selectAtom(formAtom, (o) => o.record.id), [formAtom]),
  );
  const parentVersion = useAtomValue(
    useMemo(() => selectAtom(formAtom, (o) => o.record.version), [formAtom]),
  );
  const parentModel = useAtomValue(
    useMemo(() => selectAtom(formAtom, (o) => o.model), [formAtom]),
  );

  const setUpload = useFormFieldSetter(formAtom, "$upload");
  const setFileSize = useFormFieldSetter(formAtom, "fileSize");
  const setFileName = useFormFieldSetter(formAtom, "fileName");
  const setFileType = useFormFieldSetter(formAtom, "fileType");
  const setFormAtom = useSetAtom(formAtom);

  const record = {
    id: parentId,
    version: parentVersion,
    _model: parentModel,
  } as DataRecord;

  const isMetaModel = parentModel === META_FILE_MODEL;

  function canDownload() {
    if ((record?.id ?? -1) < 0) return false;
    if (isMetaModel) {
      return !!record.fileName;
    }
    return true;
  }

  function handleUpload() {
    const input = inputRef.current;
    input && input.click();
  }

  function handleDownload() {
    if (!value || typeof value !== "string") return; // validar que sea string

    const blob = base64ToBlob(value, "application/pdf");

    const url = URL.createObjectURL(blob);

    const a = document.createElement("a");
    a.href = url;
    a.download = record.fileName || schema.name; // usar nombre guardado
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);    
    /*const { target, name } = schema;
    const imageURL = makeImageURL(record, target, name, record, true);
    download(imageURL, record?.fileName || name);*/
  }

  function handleRemove() {
    const input = inputRef.current;
    input && (input.value = "");
    setUpload(undefined);
    setFileSize(undefined);
    setFileType(undefined);
    isMetaModel && setFileName(undefined);
    setValue(null);
  }

  const setDirty = useAtomCallback(
    useCallback(
      (get, set) => {
        set(formAtom, formDirtyUpdater);
        set(dirtyAtom, true);
      },
      [formAtom, dirtyAtom],
    ),
  );

  async function handleInputChange(e: ChangeEvent<HTMLInputElement>) {
    const file = e?.target?.files?.[0];
    if(!file) return;
    inputRef.current && (inputRef.current.value = "");

    if (file && validateFileSize(file)) {
      const base64 = await readFileAsBase64(file);
      const newExpedienteFile: DataRecord = {
        _model: META_FILE_MODEL,
        id: -1, // New record, so we set a negative ID
        fileName: file.name,
        fileSize: file.size,
        fileType: file.type,
        value: base64,
      }
      /*setValue(base64);
      setFileName(file.name);
      setFileSize(file.size);
      setFileType(file.type);
      setDirty();*/
      // Actualizar el formAtom para añadir a anexo2
      setFormAtom((prev) => {
        const prevAnexo2 = prev.record.anexo2 ?? [];
        return {
          ...prev,
          record: {
            ...prev.record,
            anexo2: [...prevAnexo2, newExpedienteFile],
          },
        };
      });

      // Además, si quieres marcar como dirty:
      setDirty();

    }
  }

  return (
    <FieldControl {...props}>
      <Box d="flex">
        <form ref={formRef}>
          <Box
            as={"input"}
            onChange={handleInputChange}
            type="file"
            ref={inputRef}
            d="none"
            accept={accept}
            multipe={false}
          />
        </form>
        <ButtonGroup border>
          {!readonly && (
            <Button
              title={i18n.get("Upload")}
              variant="light"
              d="flex"
              alignItems="center"
              onClick={handleUpload}
            >
              <MaterialIcon icon="upload" />
            </Button>
          )}
          {canDownload() && (
            <Button
              title={i18n.get("Download")}
              variant="light"
              d="flex"
              alignItems="center"
              onClick={handleDownload}
            >
              <MaterialIcon icon="download" />
            </Button>
          )}
          {!readonly && (
            <Button
              title={i18n.get("Remove")}
              variant="light"
              d="flex"
              alignItems="center"
              onClick={handleRemove}
            >
              <MaterialIcon icon="close" />
            </Button>
          )}
        </ButtonGroup>
      </Box>
    </FieldControl>
  );
}
