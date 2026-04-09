package com.axelor.db.modelservice.businessmessage.internal;

import com.axelor.db.Model;
import com.axelor.db.modelservice.businessmessage.BusinessMessage;
import com.axelor.db.modelservice.businessmessage.BusinessMessages;
import com.axelor.rpc.ActionResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusinessMessageHelper  {


    public static Map<String,Object> getAsMap(BusinessMessages businessMessages) {
        List<Map<String,String>> errorMensajes=new ArrayList<>();

        if (businessMessages!=null)  {
            for (BusinessMessage businessMessage : businessMessages.removeDuplicates()) {
                String fieldName = businessMessage.getFieldName();
                String message = businessMessage.getMessage();
                String label = businessMessage.getLabel();

                Map<String, String> errorMensaje = new HashMap<>();
                errorMensaje.put("fieldName", fieldName);
                errorMensaje.put("message", message);
                errorMensaje.put("label", label);
                errorMensajes.add(errorMensaje);
            }
        }



        return Map.of("errorMensajes",errorMensajes);
    }

    public static List<Map<String,String>> getAsList(BusinessMessages businessMessages) {
        List<Map<String,String>> errorMensajes=new ArrayList<>();

        if (businessMessages!=null)  {
            for (BusinessMessage businessMessage : businessMessages.removeDuplicates()) {
                String fieldName = businessMessage.getFieldName();
                String message = businessMessage.getMessage();
                String label = businessMessage.getLabel();

                Map<String, String> errorMensaje = new HashMap<>();
                errorMensaje.put("fieldName", fieldName);
                errorMensaje.put("message", message);
                errorMensaje.put("label", label);
                errorMensajes.add(errorMensaje);
            }
        }

        return errorMensajes;
    }


}

