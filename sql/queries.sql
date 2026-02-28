-- Полезные SQL запросы для работы с БД

-- Просмотр всех заявок
SELECT 
    s.id,
    s.user_id,
    s.username,
    s.status,
    s.user_description,
    s.admin_description,
    s.created_at,
    STRING_AGG(t.name, ', ') as tags
FROM submissions s
LEFT JOIN submission_tags st ON s.id = st.submission_id
LEFT JOIN tags t ON st.tag_id = t.id
GROUP BY s.id
ORDER BY s.created_at DESC;

-- Просмотр заявок на рассмотрении
SELECT * FROM submissions 
WHERE status = 'PENDING' 
ORDER BY created_at DESC;

-- Просмотр одобренных заявок
SELECT * FROM submissions 
WHERE status = 'APPROVED' 
ORDER BY created_at DESC;

-- Статистика по заявкам
SELECT 
    status,
    COUNT(*) as count
FROM submissions
GROUP BY status;

-- Популярные теги
SELECT 
    t.name,
    COUNT(st.submission_id) as usage_count
FROM tags t
LEFT JOIN submission_tags st ON t.id = st.tag_id
GROUP BY t.id, t.name
ORDER BY usage_count DESC;

-- Заявки конкретного пользователя
SELECT * FROM submissions 
WHERE user_id = :user_id 
ORDER BY created_at DESC;

-- Все теги заявки
SELECT t.* 
FROM tags t
JOIN submission_tags st ON t.id = st.tag_id
WHERE st.submission_id = :submission_id;
