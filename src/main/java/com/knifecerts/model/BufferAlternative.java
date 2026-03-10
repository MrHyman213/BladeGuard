package com.knifecerts.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BufferAlternative implements Serializable {

    @Column(name = "alt_model_name", nullable = false)
    private String modelName;

    @Column(name = "alt_brand_name")
    private String brandName;

    public BufferAlternative() {}

    public BufferAlternative(String modelName, String brandName) {
        this.modelName = modelName;
        this.brandName = brandName;
    }

    public BufferAlternative(String modelName){
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    @Override
    public String toString() {
        if (brandName != null) {
            return brandName + " / " + modelName;
        }
        return modelName;
    }
}
