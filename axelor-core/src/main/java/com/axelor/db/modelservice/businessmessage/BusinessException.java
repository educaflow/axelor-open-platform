package com.axelor.db.modelservice.businessmessage;

public class BusinessException extends Exception {

    private BusinessMessages businessMessages=new BusinessMessages();
    private Long id;

    public BusinessException() {

    }

    public BusinessException(BusinessMessages businessMessages) {
        if (businessMessages == null) {
            throw new IllegalArgumentException("BusinessMessages no puede ser null");
        }
        if (businessMessages.isEmpty()) {
            throw new IllegalArgumentException("BusinessMessages no puede estar vacío");
        }
        this.businessMessages = businessMessages;
    }

    public BusinessException(BusinessMessage businessMessage) {
        this.businessMessages.add(businessMessage);
    }
    public BusinessException( String message) {
        this.businessMessages.add(new BusinessMessage(null, message,null));
    }
    public BusinessException(String fieldName, String message) {
        this.businessMessages.add(new BusinessMessage(fieldName, message,null));
    }
    public BusinessException(String fieldName, String message, String label) {
        this.businessMessages.add(new BusinessMessage(fieldName, message, label));
    }
    public BusinessMessages getBusinessMessages() {
        return businessMessages.removeDuplicates();
    }

    public BusinessException addBusinessMessage(BusinessMessage businessMessage) {
        this.businessMessages.add(businessMessage);
        return this;
    }
    public BusinessException addBusinessMessage(String message) {
        this.businessMessages.add(new BusinessMessage(null, message,null));
        return this;
    }
    public BusinessException addBusinessMessage(String fieldName, String message) {
        this.businessMessages.add(new BusinessMessage(fieldName, message,null));
        return this;
    }
    public BusinessException addBusinessMessage(String fieldName, String message, String label) {
        this.businessMessages.add(new BusinessMessage(fieldName, message, label));
        return this;
    }
}
