package com.knifecerts.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.knifecerts.model.Submission;
import com.knifecerts.model.SubmissionStatus;

/**
 * Spring Data JPA репозиторий для доступа к данным заявок на сертификаты.
 * 
 * Предоставляет стандартные CRUD операции через JpaRepository и
 * дополнительные методы запросов для фильтрации и поиска заявок.
 * 
 * Требования: 5.1, 7.1
 */
@Repository
public interface SubmissionRepository extends JpaRepository<Submission, Long> {
    
    /**
     * Находит все заявки с указанным статусом, отсортированные по дате создания (по возрастанию).
     * 
     * Используется для получения списка ожидающих заявок для модераторов.
     * Сортировка по возрастанию обеспечивает обработку заявок в порядке поступления (FIFO).
     * 
     * @param status Статус заявки для фильтрации (PENDING, APPROVED, REJECTED)
     * @return Список заявок с указанным статусом, отсортированный по дате создания
     * 
     * Требование: 7.1 - Получение всех заявок со статусом PENDING
     */
    List<Submission> findByStatusOrderByCreatedAtAsc(SubmissionStatus status);
    
    /**
     * Находит все заявки, поданные конкретным пользователем.
     * 
     * Используется для получения истории заявок пользователя.
     * 
     * @param userId Telegram ID пользователя
     * @return Список всех заявок пользователя
     * 
     * Требование: 5.1 - Доступ к заявкам по пользователю
     */
    List<Submission> findByUserId(Long userId);
    
    List<Submission> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Находит все заявки с указанным статусом.
     * 
     * @param status Статус заявки для фильтрации
     * @return Список заявок с указанным статусом
     */
    List<Submission> findByStatus(SubmissionStatus status);
    
    /**
     * Подсчитывает количество заявок с указанным статусом.
     * 
     * Используется для статистики и проверки наличия ожидающих заявок.
     * 
     * @param status Статус заявки для подсчета (PENDING, APPROVED, REJECTED)
     * @return Количество заявок с указанным статусом
     * 
     * Требование: 7.1 - Проверка наличия ожидающих заявок
     */
    long countByStatus(SubmissionStatus status);
    
    /**
     * Находит одобренные заявки по названию или индексу (поиск без учета регистра).
     * 
     * @param name название для поиска
     * @param indexCode индекс для поиска
     * @return список найденных заявок
     */
    List<Submission> findByStatusAndNameContainingIgnoreCaseOrStatusAndIndexCodeContainingIgnoreCase(
        SubmissionStatus status1, String name, SubmissionStatus status2, String indexCode);
}
