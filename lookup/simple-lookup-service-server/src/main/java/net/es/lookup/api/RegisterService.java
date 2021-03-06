package net.es.lookup.api;

import java.util.Map.Entry;
import net.es.lookup.common.LeaseManager;
import net.es.lookup.common.Message;
import net.es.lookup.common.ReservedKeys;
import net.es.lookup.common.ReservedValues;
import net.es.lookup.common.exception.api.BadRequestException;
import net.es.lookup.common.exception.api.ForbiddenRequestException;
import net.es.lookup.common.exception.api.InternalErrorException;
import net.es.lookup.common.exception.api.UnauthorizedException;
import net.es.lookup.common.exception.internal.DataFormatException;
import net.es.lookup.common.exception.internal.DatabaseException;
import net.es.lookup.common.exception.internal.DuplicateEntryException;
import net.es.lookup.database.ServiceElasticSearch;
import net.es.lookup.protocol.json.JSONMessage;
import net.es.lookup.protocol.json.JSONRegisterRequest;
import net.es.lookup.protocol.json.JSONRegisterResponse;
import net.es.lookup.publish.Publisher;
import net.es.lookup.service.LookupService;
import net.es.lookup.service.PublishService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import java.net.URISyntaxException;
import java.util.*;

public class RegisterService {

  private static Logger Log = LogManager.getLogger(RegisterService.class);

  public String registerService(String message) {

    Log.info(" Processing register service.");
    Log.info(" Received message: " + message);
    JSONRegisterResponse response;
    JSONRegisterRequest request = new JSONRegisterRequest(message);

    if (request.getStatus() == JSONRegisterRequest.INCORRECT_FORMAT) {
      Log.info("Register status: FAiled; exiting");
      Log.error("Incorrect Json Data format");
      throw new BadRequestException("Error parsing Json elements.");
    }
    Log.debug("valid?" + this.isValid(request));
    if (this.isValid(request) && this.isAuthed(request)) {
      // Requesting a lease
      boolean gotLease = LeaseManager.getInstance().requestLease(request);

      if (gotLease) {

        String recordType = request.getRecordType();

        String uri = this.newUri(recordType);
        request.add(ReservedKeys.RECORD_URI, uri);

        // Add the state
        request.add(ReservedKeys.RECORD_STATE, ReservedValues.RECORD_VALUE_STATE_REGISTER);

        // Build the matching query requestURl that must fail for the service to be published
        Message query = new Message();
        Message operators = new Message();

        operators.add(ReservedKeys.RECORD_OPERATOR, ReservedValues.RECORD_OPERATOR_ALL);


        Map<String, Object> keyValues = request.getMap();

        for (Object o : keyValues.entrySet()) {

          Entry<String, Object> pairs = (Entry) o;

          if (!isIgnoreKey(pairs.getKey())) {

            Log.debug("key-value pair:" + pairs.getKey() + "=" + pairs.getValue());
            operators.add(pairs.getKey(), ReservedValues.RECORD_OPERATOR_ALL);
            query.add(pairs.getKey(), pairs.getValue());
          }
        }
        try {
          ServiceElasticSearch db = ServiceElasticSearch.getInstance();
          try {
            Message res = db.queryAndPublishService(request, query, operators);

            System.gc(); // Todo fix memory management
            response = new JSONRegisterResponse(res.getMap());
            String responseString;
            try {
              responseString = JSONMessage.toString(response);
            } catch (DataFormatException e) {

              Log.fatal("Data formatting exception");
              Log.info("Register status: FAILED due to Data formatting error; exiting");
              throw new InternalErrorException(
                  "Error in creating response. Data formatting exception at server.");
            }
            Log.info("Register status: SUCCESS; exiting");
            Log.debug("response:" + responseString);

            // Todo deprecated?
            if (PublishService.isServiceOn()) {
              Publisher publisher = Publisher.getInstance();
              publisher.eventNotification(res);
            }
            return responseString;
          } catch (ElasticsearchException e) {
            Log.error("ElasticSearch Exception" + e.getDetailedMessage());
            throw new ElasticsearchException(e.getMessage());
          }


        } catch (DuplicateEntryException e) {
          Log.error("FobiddenRequestException:" + e.getMessage());
          Log.info("Register status: FAILED due to Duplicate Entry; exiting");
          throw new ForbiddenRequestException(e.getMessage());
        } catch (DatabaseException e) {

          Log.error("Error connecting with database");
          throw new InternalErrorException("Error connecting to database");
        }

      } else {

        // Build response
        Log.fatal("Failed to secure lease for the registration record");
        Log.info("Register status: FAILED; exiting");
        throw new ForbiddenRequestException("Failed to secure lease for the registration record");
      }
    } else {

      if (!this.isValid(request)) {

        Log.error("Invalid request");
        Log.info("Register status: FAILED due to Invalid Request; exiting");
        throw new BadRequestException("Invalid request. Please check the key-value pairs");

      } else if (!this.isAuthed(request)) {

        Log.error("Not authorized to perform the request");
        Log.info("Register status: FAILED; exiting");
        throw new UnauthorizedException("Not authorized to perform the request");
      }
    }

    return "\n";
  }

  private boolean isAuthed(JSONRegisterRequest request) {

    // The only case where a service registration is denied is when a service with the same name,
    // same type with
    // the same client-uuid: this ensures that a service entry with a specified client-uuid cannot
    // be overwritten.
    // TODO: needs to be implemented
    return true;
  }

  private boolean isValid(JSONRegisterRequest request) {
    boolean res = true; //request.validate(); Todo made changes to this
    return (res && request.getRecordType() != null && !request.getRecordType().isEmpty());
  }

  private String newUri(String recordType) {

    if (recordType != null && !recordType.isEmpty()) {
      return
          LookupService.SERVICE_URI_PREFIX + "/" + recordType + "/" + UUID.randomUUID().toString();
    } else {
      Log.error("Error creating URI: Record Type not found");
      throw new BadRequestException("Cannot create URI. Record Type not found");
    }
  }

  private boolean isIgnoreKey(String key) {

    if (key.equals(ReservedKeys.RECORD_TTL)
        || key.equals(ReservedKeys.RECORD_EXPIRES)
        || key.equals(ReservedKeys.RECORD_URI) 
        || key.equals(ReservedKeys.RECORD_STATE)){
      return true;
    } else {
      return false;
    }
  }
}
