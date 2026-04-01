package com.knifecerts.model;

public interface Moderatable {
    String getModelName();
    String getBrandName();
    String getIndexValue();
    String getAlternativesText(String separator);
}
