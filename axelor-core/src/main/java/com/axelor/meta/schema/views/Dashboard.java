/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.meta.schema.views;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

@XmlType
@JsonTypeName("dashboard")
public class Dashboard extends AbstractView implements ContainerView {

  @XmlElement(name = "field")
  @XmlElementWrapper(name = "search-fields")
  private List<BaseSearchField> searchFields;

  @XmlElements({@XmlElement(name = "dashlet", type = Dashlet.class)})
  private List<AbstractWidget> items;

  @XmlAttribute private String onInit;
  @XmlAttribute private String searchModel;

  @Override
  public List<AbstractWidget> getItems() {
    return items;
  }

  public void setItems(List<AbstractWidget> items) {
    this.items = items;
  }

  public String getOnInit() {
    return onInit;
  }

  public void setOnInit(String onInit) {
    this.onInit = onInit;
  }

  public String getSearchModel() {
    return searchModel;
  }

    public void setSearchModel(String searchModel) {
        this.searchModel = searchModel;
    }
  public List<BaseSearchField> getSearchFields() {
    return searchFields;
  }

  public void setSearchFields(List<BaseSearchField> searchFields) {
    this.searchFields = searchFields;
  }
}
