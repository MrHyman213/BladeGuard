package com.knifecerts.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_main_menu")
public class UserMainMenu {
    
    @Id
    @Column(name = "chat_id")
    private Long chatId;
    
    @Column(name = "message_id", nullable = false)
    private Integer messageId;
    
    public UserMainMenu() {}
    
    public UserMainMenu(Long chatId, Integer messageId) {
        this.chatId = chatId;
        this.messageId = messageId;
    }
    
    public Long getChatId() {
        return chatId;
    }
    
    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }
    
    public Integer getMessageId() {
        return messageId;
    }
    
    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    @Override
    public String toString() {
        return "UserMainMenu{chatId=" + chatId + ", messageId=" + messageId + "}";
    }
}
