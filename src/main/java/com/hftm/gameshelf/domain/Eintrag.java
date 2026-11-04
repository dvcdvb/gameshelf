package com.hftm.gameshelf.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "eintrag")
public class Eintrag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spieler_id", nullable = false, updatable = false)
    private Spieler spieler;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spiel_id", nullable = false, updatable = false)
    private Spiel spiel;

    // STRING statt ORDINAL: in der DB steht "GESPIELT" statt 2 -> lesbar und robust gegen Umsortieren
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "rating")
    private Short rating;

    @Column(name = "bewertungstext", length = 2000)
    private String bewertungstext;

    @Column(name = "spielzeit_std", precision = 6, scale = 1)
    private BigDecimal spielzeitStd;

    @Column(name = "gestartet_am")
    private LocalDate gestartetAm;

    @Column(name = "abgeschlossen_am")
    private LocalDate abgeschlossenAm;

    // Optimistic Locking (T5): Hibernate erhöht die Version bei jedem Update und prüft sie im WHERE
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "aktualisiert_am", nullable = false)
    private OffsetDateTime aktualisiertAm;

    protected Eintrag() {
    }

    public Eintrag(Spieler spieler, Spiel spiel, Status status) {
        this.spieler = spieler;
        this.spiel = spiel;
        this.status = status;
    }

    @PrePersist
    @PreUpdate
    void zeitstempelSetzen() {
        this.aktualisiertAm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public Spieler getSpieler() { return spieler; }
    public Spiel getSpiel() { return spiel; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Short getRating() { return rating; }
    public void setRating(Short rating) { this.rating = rating; }
    public String getBewertungstext() { return bewertungstext; }
    public void setBewertungstext(String bewertungstext) { this.bewertungstext = bewertungstext; }
    public BigDecimal getSpielzeitStd() { return spielzeitStd; }
    public void setSpielzeitStd(BigDecimal spielzeitStd) { this.spielzeitStd = spielzeitStd; }
    public LocalDate getGestartetAm() { return gestartetAm; }
    public void setGestartetAm(LocalDate gestartetAm) { this.gestartetAm = gestartetAm; }
    public LocalDate getAbgeschlossenAm() { return abgeschlossenAm; }
    public void setAbgeschlossenAm(LocalDate abgeschlossenAm) { this.abgeschlossenAm = abgeschlossenAm; }
    public Long getVersion() { return version; }
    public OffsetDateTime getAktualisiertAm() { return aktualisiertAm; }
}
