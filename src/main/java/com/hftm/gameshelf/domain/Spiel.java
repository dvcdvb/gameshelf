package com.hftm.gameshelf.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "spiel")
public class Spiel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: Das Genre wird nur geladen, wenn es wirklich gebraucht wird (Default bei ManyToOne wäre EAGER)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "genre_id", nullable = false)
    private Genre genre;

    @Column(nullable = false, length = 200)
    private String titel;

    @Column(name = "erscheinungsjahr")
    private Short erscheinungsjahr;

    @Column(nullable = false)
    private boolean aktiv = true;

    // Bewusste Redundanz: wird nur über erfasseBewertung / entferneBewertung verändert
    @Column(name = "anzahl_bewertungen", nullable = false)
    private int anzahlBewertungen = 0;

    @Column(name = "rating_summe", nullable = false)
    private int ratingSumme = 0;

    protected Spiel() {
    }

    public Spiel(Genre genre, String titel, Short erscheinungsjahr) {
        this.genre = genre;
        this.titel = titel;
        this.erscheinungsjahr = erscheinungsjahr;
    }

    public void deaktivieren() { this.aktiv = false; }
    public void aktivieren()   { this.aktiv = true; }

    /** Durchschnittsrating oder null, wenn noch keine Bewertung vorhanden ist. */
    public Double durchschnittsRating() {
        return anzahlBewertungen == 0 ? null : (double) ratingSumme / anzahlBewertungen;
    }

    public Long getId() { return id; }
    public Genre getGenre() { return genre; }
    public void setGenre(Genre genre) { this.genre = genre; }
    public String getTitel() { return titel; }
    public void setTitel(String titel) { this.titel = titel; }
    public Short getErscheinungsjahr() { return erscheinungsjahr; }
    public void setErscheinungsjahr(Short erscheinungsjahr) { this.erscheinungsjahr = erscheinungsjahr; }
    public boolean isAktiv() { return aktiv; }
    public int getAnzahlBewertungen() { return anzahlBewertungen; }
    public int getRatingSumme() { return ratingSumme; }
}
