package com.knifecerts.model;

import com.knifecerts.dto.AlternativeEntry;
import com.knifecerts.service.AlternativesParser;
import com.knifecerts.service.SettingsService;

import java.util.*;
import java.util.stream.Collectors;

public class ModerationState {

    private final Moderatable original;
    private final AlternativesParser parser;
    private final SettingsService settingsService;

    private String name;
    private String brand;
    private String indexCode;
    private List<String> alternativeModels;
    private Integer formMessageId;
    private Integer promptMessageId;
    private Integer confirmationMessageId;
    private Integer duplicateMessageId;
    private String editingField;
    private Set<Long> transitiveAlternativeIds;
    private Integer transitiveMessageId;
    private Long approvedKnifeId;
    private Long duplicateKnifeId;
    private String photoPath;

    public ModerationState(AlternativesParser parser, SettingsService settingsService) {
        this.original = null;
        this.parser = parser;
        this.settingsService = settingsService;
        this.name = "";
        this.brand = "";
        this.indexCode = "";
        this.alternativeModels = new ArrayList<>();
    }

    public ModerationState(Moderatable original, AlternativesParser parser, SettingsService settingsService) {
        this.original = original;
        this.parser = parser;
        this.settingsService = settingsService;
        String separator = settingsService.getAlternativeSeparator();
        this.name = original.getModelName();
        this.brand = original.getBrandName();
        this.indexCode = original.getIndexValue();
        this.alternativeModels = parser.parse(original.getAlternativesText(separator)).stream()
                .map(entry -> format(entry, separator))
                .collect(Collectors.toList());
    }

    public boolean hasChanges() {
        if (original == null) {
            return !name.isEmpty() || !brand.isEmpty() || !indexCode.isEmpty() || !alternativeModels.isEmpty();
        }
        String separator = settingsService.getAlternativeSeparator();
        return !Objects.equals(original.getModelName(), name)
                || !Objects.equals(original.getBrandName(), brand)
                || !Objects.equals(original.getIndexValue(), indexCode)
                || !Objects.equals(
                original.getAlternativesText(separator),
                String.join(", ", alternativeModels)
        );
    }

    private String format(AlternativeEntry alt, String separator) {
        return (alt.brand() != null ? alt.brand() + " " + separator + " " : "") + alt.name();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getIndexCode() { return indexCode; }
    public void setIndexCode(String indexCode) { this.indexCode = indexCode; }
    public List<String> getAlternativeModels() { return alternativeModels; }
    public void setAlternativeModels(List<String> alternativeModels) { this.alternativeModels = alternativeModels; }
    public Integer getFormMessageId() { return formMessageId; }
    public void setFormMessageId(Integer formMessageId) { this.formMessageId = formMessageId; }
    public Integer getPromptMessageId() { return promptMessageId; }
    public void setPromptMessageId(Integer promptMessageId) { this.promptMessageId = promptMessageId; }
    public String getEditingField() { return editingField; }
    public void setEditingField(String editingField) { this.editingField = editingField; }
    public Set<Long> getTransitiveAlternativeIds() { return transitiveAlternativeIds; }
    public void setTransitiveAlternativeIds(Set<Long> transitiveAlternativeIds) { this.transitiveAlternativeIds = transitiveAlternativeIds; }
    public Integer getTransitiveMessageId() { return transitiveMessageId; }
    public void setTransitiveMessageId(Integer transitiveMessageId) { this.transitiveMessageId = transitiveMessageId; }
    public Long getApprovedKnifeId() { return approvedKnifeId; }
    public void setApprovedKnifeId(Long approvedKnifeId) { this.approvedKnifeId = approvedKnifeId; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public Moderatable getOriginal() { return original; }

    public Integer getConfirmationMessageId() {
        return confirmationMessageId;
    }

    public void setConfirmationMessageId(Integer confirmationMessageId) {
        this.confirmationMessageId = confirmationMessageId;
    }

    public Long getDuplicateKnifeId() {
        return duplicateKnifeId;
    }

    public void setDuplicateKnifeId(Long duplicateKnifeId) {
        this.duplicateKnifeId = duplicateKnifeId;
    }

    public Integer getDuplicateMessageId() {
        return duplicateMessageId;
    }

    public void setDuplicateMessageId(Integer duplicateMessageId) {
        this.duplicateMessageId = duplicateMessageId;
    }
}