package com.knifecerts.bot.state;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Класс для хранения ID сообщений чата.
 * Отслеживает главное меню, окна и другие сообщения для управления их жизненным циклом.
 */
public class ChatMessages {
    private Integer mainMenuMessageId;
    private Integer lastWindowMessageId; // ID последнего сообщения окна
    private Integer pendingListMessageId; // ID сообщения со списком ожидающих заявок
    private final Set<Integer> otherMessageIds = new HashSet<>();
    private final List<Integer> recentWindowMessages = new ArrayList<>(); // Последние сообщения окон
    
    public Integer getMainMenuMessageId() { 
        return mainMenuMessageId; 
    }
    
    public void setMainMenuMessageId(Integer id) { 
        this.mainMenuMessageId = id; 
    }
    
    public Integer getLastWindowMessageId() { 
        return lastWindowMessageId; 
    }
    
    public void setLastWindowMessageId(Integer id) { 
        this.lastWindowMessageId = id;
        if (id != null) {
            recentWindowMessages.add(id);
            System.out.println("DEBUG ChatMessages: Added message " + id + " to list, size now: " + recentWindowMessages.size());
            // Храним только последние 10
            if (recentWindowMessages.size() > 10) {
                recentWindowMessages.removeFirst();
            }
        }
    }
    
    public List<Integer> getRecentWindowMessages() { 
        System.out.println("DEBUG ChatMessages: getRecentWindowMessages called, size: " + recentWindowMessages.size());
        return new ArrayList<>(recentWindowMessages); 
    }
    
    public void clearRecentWindowMessages() { 
        System.out.println("DEBUG ChatMessages: clearRecentWindowMessages called, was size: " + recentWindowMessages.size());
        recentWindowMessages.clear(); 
    }
    
    public Set<Integer> getOtherMessageIds() { 
        return otherMessageIds; 
    }
    
    public void addOtherMessageId(Integer id) { 
        if (id != null && !id.equals(mainMenuMessageId)) {
            otherMessageIds.add(id); 
        }
    }
    
    public void clearOtherMessages() { 
        otherMessageIds.clear(); 
    }
    
    public Integer getPendingListMessageId() { 
        return pendingListMessageId; 
    }
    
    public void setPendingListMessageId(Integer id) { 
        this.pendingListMessageId = id; 
    }
}
