/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.axelor.TestingHelpers;
import com.axelor.db.JPA;
import com.axelor.test.db.Address;
import com.axelor.test.db.Contact;
import com.axelor.test.service.impl.AddressNoteServiceImpl;
import com.axelor.test.service.impl.AddressServiceImpl;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import jakarta.validation.ValidationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests del {@code ModelServiceValidationWalker}: al guardar/borrar un maestro por {@code
 * Resource}, los detalles de composición (Contact → addresses → notes) se validan con el {@code
 * ModelService} de cada entidad hija ({@code AddressServiceImpl} / {@code AddressNoteServiceImpl},
 * servicios de test con centinelas y contadores).
 */
public class ResourceDetailValidationTest extends RpcTest {

  private static final AtomicLong SEQ = new AtomicLong();

  @Inject Resource<Contact> resource;

  @BeforeEach
  @Transactional
  public void setUp() {
    ensureAuth("admin", "admin");
    AddressServiceImpl.reset();
    AddressNoteServiceImpl.reset();
  }

  @AfterAll
  static void tearDown() {
    TestingHelpers.logout();
  }

  @Test
  @Transactional
  public void testInsertHijoInvalido() {
    Map<String, Object> data = newContactData();
    data.put("addresses", List.of(newAddressData(AddressServiceImpl.INVALID_INSERT + " calle")));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    ValidationException e = assertThrows(ValidationException.class, () -> resource.save(req));

    assertTrue(e.getMessage().contains("addresses["), e.getMessage());
    assertTrue(e.getMessage().contains("street inválido en insert"), e.getMessage());
    assertEquals(1, AddressServiceImpl.INSERT_CALLS.get());
  }

  @Test
  @Transactional
  public void testUpdateHijoInvalido() {
    Contact contact = createContactWithAddresses("Calle Vieja 1");
    Address address = contact.getAddresses().get(0);

    Map<String, Object> childJson = existingAddressData(address);
    childJson.put("street", AddressServiceImpl.INVALID_UPDATE + " calle");
    Map<String, Object> data = existingContactData(contact);
    data.put("addresses", List.of(childJson));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    ValidationException e = assertThrows(ValidationException.class, () -> resource.save(req));

    assertTrue(e.getMessage().contains("street inválido en update"), e.getMessage());
    assertEquals(1, AddressServiceImpl.UPDATE_CALLS.get());
  }

  @Test
  @Transactional
  public void testUpdateHijoValidoRecibeOriginal() {
    Contact contact = createContactWithAddresses("Calle Vieja 1");
    Address address = contact.getAddresses().get(0);

    Map<String, Object> childJson = existingAddressData(address);
    childJson.put("street", "Calle Nueva 2");
    Map<String, Object> data = existingContactData(contact);
    data.put("addresses", List.of(childJson));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    Response res = resource.save(req);

    assertNotNull(res.getItem(0));
    assertEquals(1, AddressServiceImpl.UPDATE_CALLS.get());
    // El original que recibe validateUpdate es el estado pre-edición (original profundo).
    assertNotNull(AddressServiceImpl.LAST_ORIGINAL.get());
    assertEquals("Calle Vieja 1", AddressServiceImpl.LAST_ORIGINAL.get().getStreet());
  }

  @Test
  @Transactional
  public void testHijoIntactoNoSeValida() {
    Contact contact = createContactWithAddresses("Calle Intacta 1");
    Address address = contact.getAddresses().get(0);

    // El hijo viaja solo como referencia {id} (sin version) → no se valida ni se borra.
    Map<String, Object> data = existingContactData(contact);
    data.put("firstName", "Cambiado");
    data.put("addresses", List.of(Map.of("id", address.getId())));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    Response res = resource.save(req);

    assertNotNull(res.getItem(0));
    assertEquals(0, AddressServiceImpl.INSERT_CALLS.get());
    assertEquals(0, AddressServiceImpl.UPDATE_CALLS.get());
    assertEquals(0, AddressServiceImpl.REMOVE_CALLS.get());
  }

  @Test
  @Transactional
  public void testHijoConVersionNullNoSeValida() {
    Contact contact = createContactWithAddresses("Calle Intacta 2");
    Address address = contact.getAddresses().get(0);

    // Misma regla que JPA._edit: version == null (aunque la clave esté presente) → referencia
    // intacta, no se muta ni se valida.
    Map<String, Object> childJson = new HashMap<>();
    childJson.put("id", address.getId());
    childJson.put("version", null);
    Map<String, Object> data = existingContactData(contact);
    data.put("firstName", "Cambiado");
    data.put("addresses", List.of(childJson));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    Response res = resource.save(req);

    assertNotNull(res.getItem(0));
    assertEquals(0, AddressServiceImpl.INSERT_CALLS.get());
    assertEquals(0, AddressServiceImpl.UPDATE_CALLS.get());
    assertEquals(0, AddressServiceImpl.REMOVE_CALLS.get());
  }

  @Test
  @Transactional
  public void testHijoQuitadoDeLaColeccionPasaPorValidateRemove() {
    Contact contact =
        createContactWithAddresses("Calle Normal 1", AddressServiceImpl.NO_DELETE + " calle");
    Address normal = contact.getAddresses().get(0);

    // El array ya no contiene la dirección NO-DELETE → orphan removal → validateRemove falla.
    Map<String, Object> data = existingContactData(contact);
    data.put("addresses", List.of(Map.of("id", normal.getId())));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    ValidationException e = assertThrows(ValidationException.class, () -> resource.save(req));

    assertTrue(e.getMessage().contains("no se puede borrar esta dirección"), e.getMessage());
    assertEquals(1, AddressServiceImpl.REMOVE_CALLS.get());
  }

  @Test
  @Transactional
  public void testRemoveMaestroValidaDetalles() {
    Contact contact = createContactWithAddresses(AddressServiceImpl.NO_DELETE + " calle");

    Request req =
        fromJson(
            toJson(Map.of("data", Map.of("id", contact.getId(), "version", contact.getVersion()))),
            Request.class);

    ValidationException e =
        assertThrows(ValidationException.class, () -> resource.remove(contact.getId(), req));

    assertTrue(e.getMessage().contains("no se puede borrar esta dirección"), e.getMessage());
    assertEquals(1, AddressServiceImpl.REMOVE_CALLS.get());
  }

  @Test
  @Transactional
  public void testRemoveMasivoValidaDetalles() {
    Contact contact = createContactWithAddresses(AddressServiceImpl.NO_DELETE + " calle");

    Request req =
        fromJson(
            toJson(
                Map.of(
                    "records",
                    List.of(Map.of("id", contact.getId(), "version", contact.getVersion())))),
            Request.class);

    ValidationException e = assertThrows(ValidationException.class, () -> resource.remove(req));

    assertTrue(e.getMessage().contains("no se puede borrar esta dirección"), e.getMessage());
    assertEquals(1, AddressServiceImpl.REMOVE_CALLS.get());
  }

  @Test
  @Transactional
  public void testNietoInvalido() {
    Map<String, Object> addressJson = newAddressData("Calle Con Notas 1");
    addressJson.put("notes", List.of(Map.of("note", AddressNoteServiceImpl.INVALID_NOTE + " x")));
    Map<String, Object> data = newContactData();
    data.put("addresses", List.of(addressJson));

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    ValidationException e = assertThrows(ValidationException.class, () -> resource.save(req));

    assertTrue(e.getMessage().contains("notes["), e.getMessage());
    assertTrue(e.getMessage().contains("nota inválida"), e.getMessage());
    assertEquals(1, AddressNoteServiceImpl.INSERT_CALLS.get());
  }

  @Test
  @Transactional
  public void testColeccionAusenteNoDesciende() {
    Contact contact = createContactWithAddresses("Calle Sin Tocar 1");

    Map<String, Object> data = existingContactData(contact);
    data.put("firstName", "SoloElMaestro");

    Request req = fromJson(toJson(Map.of("data", data)), Request.class);
    Response res = resource.save(req);

    assertNotNull(res.getItem(0));
    assertEquals(0, AddressServiceImpl.INSERT_CALLS.get());
    assertEquals(0, AddressServiceImpl.UPDATE_CALLS.get());
    assertEquals(0, AddressServiceImpl.REMOVE_CALLS.get());
  }

  /*************************************************************************************/
  /********************************      Helpers       ********************************/
  /*************************************************************************************/

  private Contact createContactWithAddresses(String... streets) {
    Contact contact = new Contact();
    contact.setFirstName("Walker");
    contact.setLastName("Test-" + SEQ.incrementAndGet());
    for (String street : streets) {
      Address address = new Address();
      address.setStreet(street);
      address.setCity("Valencia");
      address.setZip("46000");
      contact.addAddress(address);
    }
    JPA.em().persist(contact);
    JPA.em().flush();
    return contact;
  }

  private Map<String, Object> newContactData() {
    Map<String, Object> data = new HashMap<>();
    data.put("firstName", "Walker");
    data.put("lastName", "Nuevo-" + SEQ.incrementAndGet());
    return data;
  }

  private Map<String, Object> existingContactData(Contact contact) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", contact.getId());
    data.put("version", contact.getVersion());
    return data;
  }

  private Map<String, Object> newAddressData(String street) {
    Map<String, Object> address = new HashMap<>();
    address.put("street", street);
    address.put("city", "Valencia");
    address.put("zip", "46000");
    return address;
  }

  private Map<String, Object> existingAddressData(Address address) {
    Map<String, Object> data = new HashMap<>();
    data.put("id", address.getId());
    data.put("version", address.getVersion());
    return data;
  }
}
