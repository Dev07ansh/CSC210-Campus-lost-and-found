package com.csc210.backend.model;

import jakarta.persistence.*;

@Entity
public class Match {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Long lostItemId, foundItemId; private double similarityScore;
    public Match() {} public Long getId(){return id;} public Long getLostItemId(){return lostItemId;} public void setLostItemId(Long value){lostItemId=value;}
    public Long getFoundItemId(){return foundItemId;} public void setFoundItemId(Long value){foundItemId=value;} public double getSimilarityScore(){return similarityScore;} public void setSimilarityScore(double value){similarityScore=value;}
}
