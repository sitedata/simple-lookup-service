package net.es.lookup.database;
import net.es.lookup.common.LeaseManager;
import net.es.lookup.common.Message;
import net.es.lookup.common.ReservedValues;

import net.es.lookup.common.exception.internal.DatabaseException;
import net.es.lookup.common.exception.internal.DuplicateEntryException;
import net.es.lookup.common.exception.internal.RecordNotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Instant;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class ServiceElasticSearchTest {

  private ServiceElasticSearch client;
  private static Logger Log = LogManager.getLogger(ServiceElasticSearchTest.class);

  /**
   * Connects to the database an deletes all records if any exist
   *
   * @throws URISyntaxException for incorrect server name
   * @throws DatabaseException if error connecting to database
   */

  @BeforeClass
  public static void setUpDatabase() throws DatabaseException, URISyntaxException {
     new ServiceElasticSearch("localhost", 9200, 9300, "lookup");
  }

  @Before
  public void setUp() throws DatabaseException {

    client = ServiceElasticSearch.getInstance();
    client.deleteAllRecords();
  }

  /**
   * creates a message and adds it to the database
   * @throws DatabaseException If error entering data into the database
   * @throws DuplicateEntryException If message being added already exists in the database
   */
  private void queryAndPublishService() throws DatabaseException, DuplicateEntryException {
    Message message = new Message();
    

    message.add("type", "test");
    String uuid = UUID.randomUUID().toString();
    message.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message.add("test-id", String.valueOf(1));

    message.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message.add("expires", dateTime.toString());

    Message query = new Message();
    query.add("type", "test");
    query.add("test-id", String.valueOf(1));

    Message operators = new Message();
    operators.add("type", ReservedValues.RECORD_OPERATOR_ALL);
    operators.add("test-id", ReservedValues.RECORD_OPERATOR_ALL);

    Message addedMessage = client.queryAndPublishService(message, query, operators);

  }

  /**
   * Test to add a single record to the database
   *
   * @throws DatabaseException If error inserting message into database
   * @throws DuplicateEntryException If the record already exists in the database
   */
  @Test
  public void queryAndPublishSingle() throws DatabaseException, DuplicateEntryException {

    queryAndPublishService();
  }

  /** Check if duplicate entry exception is thrown when 2 dame records are added to the dastabase */
  @Test
  public void queryAndPublishExists() {
    boolean checkSecond = false;
    try {
      queryAndPublishService();
      checkSecond = true;
      Thread.sleep(1000); // Buffer time for adding record to
      queryAndPublishService();
      fail();
    } catch (DuplicateEntryException e) {
      if (checkSecond) {
        Log.info("Duplicate entry detected. Test passed");
      } else {
        Log.error("entry already exists before test");
        fail();
      }
    } catch (DatabaseException e) {

      e.printStackTrace();
      fail();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Delete an existing record using it's URI from the database
   *
   * @throws DatabaseException If unable to delete record from database
   * @throws DuplicateEntryException The record already exists before testing
   */
  @Test
  public void deleteExistingUri() throws DatabaseException, DuplicateEntryException, RecordNotFoundException {

    this.queryAndPublishService();
    Message status = client.deleteRecord("2");
    assertNotNull(status.getMap());
  }

  /**
   * Attempt to delete a URI that doesn't exist in the database
   *
   * @throws DatabaseException Error deleting the record
   * @throws DuplicateEntryException Entry already exists before test
   */
  @Test
  public void deleteNonExistingUri() throws DatabaseException, DuplicateEntryException {
    this.queryAndPublishService();
    Message status = null;
    try {
      status = client.deleteRecord("3");
    } catch (RecordNotFoundException e) {
      Log.info("Couldn't find URI, test pass");
    }
    assertNull(status);
  }

  /**
   * Gets a record that exists in the database using the URI
   * @throws DatabaseException Error deleting the record
   * @throws DuplicateEntryException Entry already exists before test
   */
  @Test
  public void getExistingRecord() throws DatabaseException, DuplicateEntryException {
    this.queryAndPublishService();
    Message response = client.getRecordByURI("2");
    assertNotNull(response.getMap());
  }

  /**
   * Attempt to retrieve a record that doesn't exist in the database
   *
   * @throws DatabaseException Error deleting the record
   * @throws DuplicateEntryException Entry already exists before test
   */
  @Test
  public void getNonExistingRecord() throws DatabaseException, DuplicateEntryException {
    this.queryAndPublishService();
    Message response = client.getRecordByURI("4");
    assertNull(response);
  }

  /**
   * Updates record that exists in the database using it's URI
   *
   * @throws DatabaseException Error deleting the record
   * @throws DuplicateEntryException Entry already exists before test
   */
  @Test
  public void updateExisting() throws DatabaseException, DuplicateEntryException, DatabaseException {
    this.queryAndPublishService();
    Message message = new Message();
    message.add("type", "test");

    String uuid = UUID.randomUUID().toString();
    message.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message.add("test-id", String.valueOf(2));

    message.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message.add("expires", dateTime.toString());

    Message response = client.updateService("2", message);
    assertNotNull(response.getMap());
  }

  /**
   * Attempt to update a record that doesn't exist in the database
   *
   * @throws DatabaseException Error deleting the record
   * @throws DuplicateEntryException Entry already exists before test
   */
  @Test
  public void updateNotExisting() throws DatabaseException, DatabaseException, DuplicateEntryException {
    this.queryAndPublishService();
    Message message = new Message();
    message.add("type", "test");

    String uuid = UUID.randomUUID().toString();
    message.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message.add("test-id", String.valueOf(2));

    message.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message.add("expires", dateTime.toString());

    try {
      Message response = client.updateService("3", message);

    } catch (DatabaseException e) {
      Log.info("Test passed, database exception was thrown for missing service ID in database");
      assert (true);
    }
  }

  /**
   * Trying to update with null URI specified
   *
   * @throws DatabaseException             Error deleting the record
   * @throws DuplicateEntryException Entry already exists before test
   * @throws DatabaseException
   */
  @Test
  public void updateEmptyServiceID() throws DuplicateEntryException, DatabaseException, DatabaseException {

    this.queryAndPublishService();
    Message message = new Message();
    message.add("type", "test");

    String uuid = UUID.randomUUID().toString();
    message.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message.add("test-id", String.valueOf(2));

    message.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message.add("expires", dateTime.toString());

    try {
      Message response = client.updateService(null, message);
    } catch (DatabaseException e) {

      Log.info("Test passed, database exception was thrown for empty service ID");
      assert (true);
    }
  }

  /**
   * Attempts to add a record that doesn't already exist to database Shouldn't check for duplicates
   *
   * @throws DatabaseException Error adding record
   */
  @Test
  public void publishServiceNotExistingTest() throws DatabaseException {
    Message message = new Message();
    message.add("type", "test");

    String uuid = UUID.randomUUID().toString();
    message.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message.add("test-id", String.valueOf(2));

    message.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message.add("expires", dateTime.toString());
    client.publishService(message);
    Message response = client.getRecordByURI("2");
    assertNotNull(response.getMap());
  }

  /**
   * Attempts to add a record that already exists to database shouldn't check for duplicates
   *
   * @throws DatabaseException Error adding record to database
   */
  @Test
  public void publishServiceExistingTest() throws DatabaseException {

    Message message = new Message();
    message.add("type", "test");

    String uuid = UUID.randomUUID().toString();
    message.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message.add("test-id", String.valueOf(2));

    message.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message.add("expires", dateTime.toString());

    client.publishService(message);
    client.publishService(message);
    Message response = client.getRecordByURI("2");
    assertNotNull(response.getMap());
  }

  /**
   * Bulk update records that exist in the database
   *
   * @throws DatabaseException unable to update records
   */
  @Test
  public void bulkUpdateAllExisting() throws DatabaseException {
    Message message1 = new Message();
    message1.add("type", "test");

    message1.add(
        "uri", "1"); // 2nd param should be uuid but for testing purposes was assigned a number

    message1.add("test-id", String.valueOf(1));

    message1.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message1.add("expires", dateTime.toString());

    Message message2 = new Message();
    message1.add("type", "test");

    message2.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message2.add("test-id", String.valueOf(2));

    message2.add("ttl", "PT10M");

    message2.add("expires", dateTime.toString());

    Message message3 = new Message();
    message3.add("type", "test");

    message3.add(
        "uri", "3"); // 2nd param should be uuid but for testing purposes was assigned a number

    message3.add("test-id", String.valueOf(3));

    message3.add("ttl", "PT10M");

    message3.add("expires", dateTime.toString());

    client.publishService(message1);
    client.publishService(message2);
    client.publishService(message3);

    Map<String, Message> messages = new HashMap<>();
    messages.put("1", message2);
    messages.put("2", message3);
    messages.put("3", message1);

    Message count = client.bulkUpdate(messages);
    assertEquals(count.getMap().get("renewed"), 3);
  }

  /**
   * Attempt to update records that don't exist in the database
   *
   * @throws DatabaseException Error updating records in the database
   */
  @Test
  public void bulkUpdateNotExisting() throws DatabaseException {

    Message message1 = new Message();
    message1.add("type", "test");

    message1.add(
        "uri", "1"); // 2nd param should be uuid but for testing purposes was assigned a number

    message1.add("test-id", String.valueOf(1));

    message1.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message1.add("expires", dateTime.toString());

    Message message2 = new Message();
    message1.add("type", "test");

    message2.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message2.add("test-id", String.valueOf(2));

    message2.add("ttl", "PT10M");

    message2.add("expires", dateTime.toString());

    Message message3 = new Message();
    message3.add("type", "test");

    message3.add(
        "uri", "3"); // 2nd param should be uuid but for testing purposes was assigned a number

    message3.add("test-id", String.valueOf(3));

    message3.add("ttl", "PT10M");

    message3.add("expires", dateTime.toString());

    client.publishService(message1);
    client.publishService(message2);
    client.publishService(message3);

    Map<String, Message> messages = new HashMap<>();
    messages.put("1", message2);
    messages.put("2", message3);
    messages.put("4", message1);

    try {
      Message count = client.bulkUpdate(messages);
    } catch (DatabaseException e) {

      Log.info("error updating due to incorrect URI; Passed test");
    }
  }

  /**
   * Delete records that have expired
   *
   * @throws DatabaseException error deleting records
   * @throws InterruptedException Deletion process interrupted
   */
  @Test
  public void deleteExpired() throws DatabaseException, InterruptedException {

    Message message1 = new Message();
    message1.add("type", "test");

    message1.add(
        "uri", "1"); // 2nd param should be uuid but for testing purposes was assigned a number

    message1.add("test-id", String.valueOf(1));

    message1.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    LeaseManager.getInstance().requestLease(message1);
   // message1.add("expires", dateTime.toString());

    Message message2 = new Message();
    message2.add("type", "test");

    message2.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message2.add("test-id", String.valueOf(2));

    message2.add("ttl", "PT10M");
    //message2.add("expires", dateTime.toString());
    LeaseManager.getInstance().requestLease(message2);


    Message message3 = new Message();
    message3.add("type", "test");

    message3.add(
        "uri", "3"); // 2nd param should be uuid but for testing purposes was assigned a number

    message3.add("test-id", String.valueOf(3));

    message3.add("ttl", "PT10M");

    //message3.add("expires", dateTime.toString());
    LeaseManager.getInstance().requestLease(message3);


    client.publishService(message1);
    client.publishService(message2);
    client.publishService(message3);

    Thread.sleep(2000);

    //DateTime dt = new DateTime();
    //dt.plus(20000);
    Instant now = new Instant();
    DateTime pruneTime = now.plus(600000).toDateTime();
    System.out.println(pruneTime.toString());
    assertEquals(client.deleteExpiredRecords(pruneTime), 3);

  }

  /**
   * Finds records in a given time range
   *
   * @throws DatabaseException Error accessing records
   * @throws InterruptedException Accessing records interrupted
   */
  @Test
  public void findRecordInTimeRange() throws DatabaseException, InterruptedException {

    Message message1 = new Message();
    message1.add("type", "test");

    message1.add(
        "uri", "1"); // 2nd param should be uuid but for testing purposes was assigned a number

    message1.add("test-id", String.valueOf(1));

    message1.add("ttl", "PT10M");

    DateTime dateTime = new DateTime();
    message1.add("expires", dateTime.toString());

    Message message2 = new Message();

    message2.add("type", "test");


    message2.add(
        "uri", "2"); // 2nd param should be uuid but for testing purposes was assigned a number

    message2.add("test-id", String.valueOf(2));

    message2.add("ttl", "PT10M");

    message2.add("expires", dateTime.toString());

    Message message3 = new Message();
    message3.add("type", "test");

    message3.add(
        "uri", "3"); // 2nd param should be uuid but for testing purposes was assigned a number

    message3.add("test-id", String.valueOf(3));

    message3.add("ttl", "PT10M");

    message3.add("expires", dateTime.toString());

    client.publishService(message1);
    client.publishService(message2);
    client.publishService(message3);

    Thread.sleep(3000);

    DateTime dt = new DateTime();
    dt.plus(2000000);
    dateTime.minus(10000);
    assertEquals(3, client.findRecordsInTimeRange(dateTime, dt).size());
  }

  /**
   * Get value of key that exists in the record of the given URI
   *
   * @throws DatabaseException Unable to access record
   * @throws DuplicateEntryException Record already exists before test
   */
  @Test
  public void getKeyExists() throws DatabaseException, DuplicateEntryException {

    this.queryAndPublishService();
    Message record = client.getRecordByURI("2");
    String key = "test-id";
    Map<String, Object> keyValueMap = new HashMap<>();
    keyValueMap.put(key, record.getKey(key));
    assertEquals("1", keyValueMap.get("test-id"));
  }

  /**
   * Get value of key that doesn't exist in record of the given URI
   *
   * @throws DatabaseException Unable to access record
   * @throws DuplicateEntryException Record already exists before test
   */
  @Test
  public void getKeyNotExists() throws DatabaseException, DuplicateEntryException {

    this.queryAndPublishService();
    Message record = client.getRecordByURI("2");
    String key = "random";
    Map<String, Object> keyValueMap = new HashMap<>();
    keyValueMap.put(key, record.getKey(key));
    assertNull(keyValueMap.get("random"));
  }
}
