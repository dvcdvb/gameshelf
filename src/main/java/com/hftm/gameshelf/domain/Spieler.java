package com.hftm.gameshelf.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "spieler")
public class Spieler {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String benutzername;

    @Column(name = "erstellt_am", nullable = false, updatable = false)
    private OffsetDateTime erstelltAm;

    protected Spieler() {
    }

    public Spieler(String benutzername) {
        this.benutzername = benutzername;
    }

    @PrePersist
    void beimErstellen() {
        this.erstelltAm = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getBenutzername() { return benutzername; }
    public void setBenutzername(String benutzername) { this.benutzername = benutzername; }
    public OffsetDateTime getErstelltAm() { return erstelltAm; }
}
