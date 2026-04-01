package com.knifecerts.model;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "knives")
public class Knife implements Moderatable{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private KnifeModel model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "idx", length = 100)
    private String index;

    @Column(name = "photo_path", length = 512)
    private String photoPath;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "alternatives",
        joinColumns = @JoinColumn(name = "knife_id"),
        inverseJoinColumns = @JoinColumn(name = "alternative_knife_id")
    )
    private Set<Knife> alternatives = new LinkedHashSet<>();

    public Knife() {}

    public Knife(KnifeModel model, Brand brand, String index, String photoPath) {
        this.model = model;
        this.brand = brand;
        this.index = index;
        this.photoPath = photoPath;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public KnifeModel getModel() {
        return model;
    }

    public void setModel(KnifeModel model) {
        this.model = model;
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getPhotoPath() {
        return photoPath;
    }

    public void setPhotoPath(String photoPath) {
        this.photoPath = photoPath;
    }

    public Set<Knife> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(Set<Knife> alternatives) {
        this.alternatives = alternatives;
    }

    public void addAlternative(Knife alternative) {
        this.alternatives.add(alternative);
        alternative.getAlternatives().add(this);
    }

    public void removeAlternative(Knife alternative) {
        this.alternatives.remove(alternative);
        alternative.getAlternatives().remove(this);
    }

    public String getDisplayName() {
        StringBuilder display = new StringBuilder();
        if (brand != null) {
            display.append(brand.getName());
        }
        if (model != null) {
            if (!display.isEmpty()) display.append(" - ");
            display.append(model.getName());
        }
        if (index != null) {
            if (!display.isEmpty()) display.append(" - ");
            display.append(index);
        }
        return !display.isEmpty() ? display.toString() : "Unknown";
    }

    @Override
    public String getModelName() {
        return model != null ? model.getName() : "";
    }

    @Override
    public String getBrandName() {
        return brand != null ? brand.getName() : "";
    }

    @Override
    public String getIndexValue() {
        return index;
    }

    @Override
    public String getAlternativesText(String separator) {
        if (alternatives == null || alternatives.isEmpty())
            return "";
        return alternatives.stream()
                .map(alt -> alt.getBrand().getName() + separator + alt.getModel().getName())
                .collect(Collectors.joining(", "));
    }
}
