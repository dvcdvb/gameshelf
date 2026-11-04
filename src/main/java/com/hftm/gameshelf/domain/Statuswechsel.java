package com.hftm.gameshelf.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Ein Eintrag im Verlauf (A4). Wird nur angelegt, nie verändert.
 */
@Entity
@Table(name = "statuswechsel")
public class Statuswechsel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eintrag_id", nullable = false, updatable = false)
    private Eintrag eintrag;

    @Enumerated(EnumType.STRING)
    @Column(name = "alter_status", length = 20, updatable = false)
    private Status alterStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "neuer_status", nullable = false, length = 20, updatable = false)
    private Status neuerStatus;

    @Column(name = "geaendert_am", nullable = false, updatable = false)
    private OffsetDateTime geaendertAm;

    protected Statuswechsel() {
    }

    public Statuswechsel(Eintrag eintrag, Status alterStatus, Status neuerStatus) {
        this.eintrag = eintrag;
        this.alterStatus = alterStatus;
        this.neuerStatus = neuerStatus;
    }

    @PrePersist
    void beimErstellen() {
        this.geaendertAm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Eintrag getEintrag() { return eintrag; }
    public Status getAlterStatus() { return alterStatus; }
    public Status getNeuerStatus() { return neuerStatus; }
    public OffsetDateTime getGeaendertAm() { return geaendertAm; }
}
