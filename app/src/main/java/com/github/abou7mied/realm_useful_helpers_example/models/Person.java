package com.github.abou7mied.realm_useful_helpers_example.models;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.Ignore;
import io.realm.annotations.PrimaryKey;

import com.google.gson.annotations.SerializedName;

public class Person extends RealmObject {

    @PrimaryKey
    @SerializedName("person_name")
    private String name;

    @SerializedName("has_dogs")
    private boolean hasDogs;

    @SerializedName("favorite_dog")
    private Dog favoriteDog;

    @SerializedName("dogs")
    private RealmList<Dog> dogs;

    @Ignore
    private transient int random;

    public void setName(String name) {
        this.name = name;
    }

    public void setHasDogs(boolean hasDogs) {
        this.hasDogs = hasDogs;
    }
}