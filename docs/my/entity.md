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