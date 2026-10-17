package com.csc210.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class FoundItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String itemName, category, color, description, location, imageUrl, status;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;
    private LocalDateTime dateTime;
    public FoundItem() {}
    public Long getId(){return id;} public String getItemName(){return itemName;} public void setItemName(String value){itemName=value;}
    public String getCategory(){return category;} public void setCategory(String value){category=value;}
    public String getColor(){return color;} public void setColor(String value){color=value;}
    public String getDescription(){return description;} public void setDescription(String value){description=value;}
    public String getLocation(){return location;} public void setLocation(String value){location=value;}
    public String getImageUrl(){return imageUrl;} public void setImageUrl(String value){imageUrl=value;}
    public String getStatus(){return status;} public void setStatus(String value){status=value;}
    public LocalDateTime getDateTime(){return dateTime;} public void setDateTime(LocalDateTime value){dateTime=value;}
    @JsonIgnore public User getReporter(){return reporter;} public void setReporter(User value){reporter=value;}
}
