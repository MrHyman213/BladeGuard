package com.knifecerts.bot.manager;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.knifecerts.model.Knife;
import com.knifecerts.model.SubmissionBuffer;
import com.knifecerts.service.AlternativesParser;
import com.knifecerts.service.AlternativesParserImpl;

/**
 * Менеджер для управления состоянием модерации заявок в AdminBot.
 * Отвечает за хранение и управление временными данными редактирования заявок.
 */
@Component
public class ModerationStateManager {

    @Autowired
    private AlternativesParser alternativesParser;

    // Хранилище состояний модерации для каждого чата
    private final Map<Long, ModerationState> moderationStates = new ConcurrentHashMap<>();

    /**
     * Получает состояние модерации для чата.
     */
    public ModerationState getState(Long chatId) {
        return moderationStates.get(chatId);
    }

    /**
     * Создает новое состояние модерации для заявки из буфера.
     */
    public ModerationState createState(Long chatId, SubmissionBuffer submission) {
        ModerationState state = new ModerationState(submission);
        moderationStates.put(chatId, state);
        return state;
    }

    /**
     * Создает новое состояние модерации для одобренного сертификата.
     */
    public ModerationState createState(Long chatId, Knife knife, boolean isApprovedView) {
        ModerationState state = new ModerationState(knife, isApprovedView);
        moderationStates.put(chatId, state);
        return state;
    }

    /**
     * Создает пустое состояние модерации.
     */
    public ModerationState createEmptyState(Long chatId) {
        ModerationState state = new ModerationState(null, false);
        moderationStates.put(chatId, state);
        return state;
    }

    /**
     * Сохраняет состояние модерации.
     */
    public void setState(Long chatId, ModerationState state) {
        moderationStates.put(chatId, state);
    }

    /**
     * Удаляет состояние модерации для чата.
     */
    public void removeState(Long chatId) {
        moderationStates.remove(chatId);
    }

    /**
     * Проверяет, есть ли активное состояние модерации для чата.
     */
    public boolean hasState(Long chatId) {
        return moderationStates.containsKey(chatId);
    }

    /**
     * Проверяет, есть ли несохраненные изменения в состоянии.
     */
    public boolean hasUnsavedChanges(Long chatId) {
        ModerationState state = moderationStates.get(chatId);
        return state != null && state.hasChanges();
    }

    /**
     * Класс для хранения состояния модерации заявки.
     */
    public class ModerationState {
        private final Object original; // SubmissionBuffer или Knife
        private String name;
        private String brand;
        private String indexCode;
        private List<String> alternativeModels;
        private Integer formMessageId;
        private Integer promptMessageId;
        private String editingField; // "name", "brand", "index", "alt"
        private boolean isApprovedView; // true если это просмотр одобренного сертификата
        private Set<Long> transitiveAlternativeIds; // ID транзитивных альтернатив
        private Integer transitiveMessageId; // ID сообщения с предложением транзитивных альтернатив
        private Long approvedKnifeId; // ID одобренного ножа (для поиска транзитивных)
        private String photoPath; // Путь к фото (для прямой загрузки)
        private Long duplicateKnifeId; // ID дубликата ножа (если найден)
        private Integer duplicateMessageId; // ID сообщения с предложением замены фото
        private Integer confirmationMessageId; // ID сообщения-подтверждения альтернатив
        private Long pendingSubmissionId; // ID заявки, на которую хотим переключиться (для подтверждения)

        public ModerationState(SubmissionBuffer original) {
            this.original = original;
            this.name = original.getModelName();
            this.brand = original.getBrandName();
            this.indexCode = original.getIndex();
            // Parse alternatives from TEXT field
            AlternativesParser parser = new AlternativesParserImpl("/");
            this.alternativeModels = parser.parse(original.getAlternatives()).stream()
                .map(alt -> (alt.brand() != null ? alt.brand() + " / " : "") + alt.name())
                .collect(Collectors.toList());
            this.isApprovedView = false;
        }

        public ModerationState(Knife original, boolean isApprovedView) {
            this.original = original;
            this.name = original.getModel().getName();
            this.brand = original.getBrand().getName();
            this.indexCode = original.getIndex();
            this.alternativeModels = original.getAlternatives().stream()
                .map(alt -> alt.getBrand().getName() + " / " + alt.getModel().getName())
                .collect(Collectors.toList());
            this.isApprovedView = isApprovedView;
        }

        public ModerationState(Object original, boolean isApprovedView) {
            this.original = original;
            this.isApprovedView = isApprovedView;
        }

        /**
         * Проверяет, были ли изменения в состоянии.
         */
        public boolean hasChanges() {
            if (original instanceof SubmissionBuffer) {
                SubmissionBuffer sub = (SubmissionBuffer) original;
                String originalName = sub.getModelName();
                String originalBrand = sub.getBrandName();
                String originalIndex = sub.getIndex();
                AlternativesParser parser = new AlternativesParserImpl("/");
                List<String> originalAlts = parser.parse(sub.getAlternatives()).stream()
                    .map(alt -> (alt.brand() != null ? alt.brand() + " / " : "") + alt.name())
                    .collect(Collectors.toList());

                boolean nameChanged = !Objects.equals(originalName, name);
                boolean brandChanged = !Objects.equals(originalBrand, brand);
                boolean indexChanged = !Objects.equals(originalIndex, indexCode);
                boolean altsChanged = !Objects.equals(originalAlts, alternativeModels);

                return nameChanged || brandChanged || indexChanged || altsChanged;
            } else if (original instanceof Knife) {
                Knife knife = (Knife) original;
                String originalName = knife.getModel().getName();
                String originalBrand = knife.getBrand().getName();
                String originalIndex = knife.getIndex();
                List<String> originalAlts = knife.getAlternatives().stream()
                    .map(alt -> alt.getBrand().getName() + " / " + alt.getModel().getName())
                    .collect(Collectors.toList());

                boolean nameChanged = !Objects.equals(originalName, name);
                boolean brandChanged = !Objects.equals(originalBrand, brand);
                boolean indexChanged = !Objects.equals(originalIndex, indexCode);
                boolean altsChanged = !Objects.equals(originalAlts, alternativeModels);

                return nameChanged || brandChanged || indexChanged || altsChanged;
            }
            return false;
        }

        // Getters and Setters
        public Object getOriginal() { return original; }
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
        public boolean isApprovedView() { return isApprovedView; }
        public void setApprovedView(boolean approvedView) { this.isApprovedView = approvedView; }
        public Set<Long> getTransitiveAlternativeIds() { return transitiveAlternativeIds; }
        public void setTransitiveAlternativeIds(Set<Long> ids) { this.transitiveAlternativeIds = ids; }
        public Integer getTransitiveMessageId() { return transitiveMessageId; }
        public void setTransitiveMessageId(Integer messageId) { this.transitiveMessageId = messageId; }
        public Long getApprovedKnifeId() { return approvedKnifeId; }
        public void setApprovedKnifeId(Long knifeId) { this.approvedKnifeId = knifeId; }
        public String getPhotoPath() { return photoPath; }
        public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
        public Long getDuplicateKnifeId() { return duplicateKnifeId; }
        public void setDuplicateKnifeId(Long knifeId) { this.duplicateKnifeId = knifeId; }
        public Integer getDuplicateMessageId() { return duplicateMessageId; }
        public void setDuplicateMessageId(Integer messageId) { this.duplicateMessageId = messageId; }
        public Integer getConfirmationMessageId() { return confirmationMessageId; }
        public void setConfirmationMessageId(Integer messageId) { this.confirmationMessageId = messageId; }
        public Long getPendingSubmissionId() { return pendingSubmissionId; }
        public void setPendingSubmissionId(Long submissionId) { this.pendingSubmissionId = submissionId; }
    }
}
