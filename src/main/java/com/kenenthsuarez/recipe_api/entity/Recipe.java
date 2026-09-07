package com.kenenthsuarez.recipe_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "recipes")
@Getter
@Setter
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "recipe_ids")
    @SequenceGenerator(name = "recipe_ids", sequenceName = "recipe_seq", allocationSize = 50)
    private Long id;
    @Version
    private long version;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 10000)
    private String description;
    @Column(nullable = false)
    private int servings;
    @Column(nullable = false)
    private boolean vegetarian;

    @ElementCollection
    @CollectionTable(name = "recipe_ingredients", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "position")
    @Column(name = "text", nullable = false, length = 1000)
    private List<String> ingredients = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "recipe_instructions", joinColumns = @JoinColumn(name = "recipe_id"))
    @OrderColumn(name = "position")
    @Column(name = "text", nullable = false, length = 2000)
    private List<String> instructions = new ArrayList<>();
}
