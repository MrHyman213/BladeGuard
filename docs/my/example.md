- изначально пустая база данных.

- юзер отправляет сертификат с наименованием "fifo" и брендом "nike". Так же он добавляет альтернативные модели ножей: loli бренда nike, filo бренда cook. 

- модератор просматривает заявку. Редактирует альтернативы, в случае необходимости. 
- модератор принимает заявку. Выводится меню: 

Вы уверены что хотите добавить в базу, следующие альтернативные модели? 

inline кнопка "nike - lofi" inline кнопка "filo - cook" (при нажатии кнопки например "nike - lofi", эта кнопка удалится из списка, посредством редактирования сообщения)

Кнопка "Принять" (удаляет сообщение и добавляет все в базу), Кнопка "Отклонить" (удаляет сообщение).

- Модератор нажимает только кнопку "принять"

- В главном меню у пользователя появляются 2 кнопки (наименования) бренда nike и cook. При нажатии кнопки nike, пользователю отправляется список с кнопками наименований fifo и loli. 

- Пользователь нажимает на кнопку "fifo".

- Отправляется фото с сертификатом. 

- Пользователь нажимает кнопку "loli".

- Отправляется сообщение "извините, но на данный момент у нас нет сертификата данной модели. Мы могли бы предложить альтернативный вариант: "

inline кнопка "fifo". 


Всего должна быть одна основная таблица - ножи.
У неё должны быть следующие поля: 
id, id_name (наименование модели, может не быть, но только в случае если есть индекс), id_brand (наименование бренда), index (varchar, индекс ножа, но его может и не быть)path (varchar, путь к файлу на диске в папке certificates). 
Логика максимально проста. При добавлении пользователем нового ножа, создается запись в таблице-буфере. У неё должны быть следующие поля:
id, name (varchar), brand (varchar), index (varchar), path (путь к сертификату на диске в папке offers).
Для альтернатив не будет отдельной таблицы. Ведь по факту, это тоже модель ножа. Единственное отличие - нет пути к файлу на диске.
Самое интересное - как будут организованы связи. 
В случае с уже одобренными сертификатами, будет таблица связей "многие ко многим":
id_certificate, id_alt_certificate, pk (id_certificate, id_alt_certificate).
Связи у заявочных моделей будут аналогичны. 
Добавление в основную таблицу из заявок будет происходить с проверками на наличие в базе новых наименований. Но явное подтверждение модератором останется только для альтернативных моделей.

Примеры entity:

```
package com.yourcompany.knives.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "brands")
@Getter
@Setter
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;
}
```
```
package com.yourcompany.knives.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "knife_models")
@Getter
@Setter
public class KnifeModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;
}
```
```
package com.yourcompany.knives.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "knives")
@Getter
@Setter
public class Knife {

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
    @JoinTable(name = "approved_alternatives",
            joinColumns = @JoinColumn(name = "knife_id"),
            inverseJoinColumns = @JoinColumn(name = "alternative_knife_id"))
    private Set<Knife> alternatives = new LinkedHashSet<>();
}
```
```
package com.yourcompany.knives.domain.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
public class BufferAlternative implements Serializable {

    @Column(name = "alt_model_name", nullable = false)
    private String modelName;

    @Column(name = "alt_brand_name", nullable = false)
    private String brandName;
}
```
```
package com.yourcompany.knives.domain.entity;

import com.yourcompany.knives.domain.embeddable.BufferAlternative;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "submissions_buffer")
@Getter
@Setter
public class SubmissionBuffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column(name = "idx", length = 100)
    private String index;

    @Column(name = "photo_path", nullable = false, length = 512)
    private String photoPath;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "buffer_alternatives", joinColumns = @JoinColumn(name = "submission_id"))
    private List<BufferAlternative> alternatives = new ArrayList<>();
}
```