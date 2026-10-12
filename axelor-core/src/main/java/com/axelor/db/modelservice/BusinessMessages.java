package com.axelor.db.modelservice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.ValidationException;

public class BusinessMessages extends ArrayList<BusinessMessage> {

    public boolean isValid() {
        return this.isEmpty();
    }

    public void throwIfInvalid() {
        if (isValid()) {
            return;
        }
        throw new ValidationException(toUserString());
    }


    public BusinessMessages removeDuplicates() {
        BusinessMessages uniqueMessages = new BusinessMessages();
        Set<BusinessMessage> seenMessages = new HashSet<>();

        for (BusinessMessage message : this) {
            if (seenMessages.add(message)) {
                uniqueMessages.add(message);
            }
        }
        return uniqueMessages;
    }


    static public BusinessMessages single(String message) {
        BusinessMessage businessMessage = new BusinessMessage(null, message, null);
        BusinessMessages businessMessages = new BusinessMessages();
        businessMessages.add(businessMessage);
        return businessMessages;
    }

    /** Texto legible para el usuario: un mensaje por línea. */
    public String toUserString() {
        return this.stream()
                .map(BusinessMessage::toString)
                .collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return this.stream()
                .map(message -> "\"" + message.toString() + "\"")
                .collect(Collectors.joining(",", "BusinessMessages=[", "]"))
                .replaceAll("[\\r\\n]", " ");
    }

}
