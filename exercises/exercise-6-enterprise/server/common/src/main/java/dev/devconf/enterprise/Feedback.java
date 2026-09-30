package dev.devconf.enterprise;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "session_feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String speaker;

    @Column(length = 512)
    private String sessionTitle;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1024)
    private String comment;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    protected Feedback() {}

    public Feedback(String speaker, String sessionTitle, int rating, String comment) {
        this.speaker      = speaker;
        this.sessionTitle = sessionTitle;
        this.rating       = rating;
        this.comment      = comment;
        this.submittedAt  = LocalDateTime.now();
    }

    public String getSpeaker()      { return speaker; }
    public String getSessionTitle() { return sessionTitle; }
    public int    getRating()       { return rating; }
    public String getComment()      { return comment; }
}
