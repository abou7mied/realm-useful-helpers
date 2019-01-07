package com.github.abou7mied.realm_useful_helpers_example.models;

import com.google.gson.annotations.SerializedName;

import io.realm.RealmObject;

public class Dog extends RealmObject {

    @SerializedName("dog_name")
    private String name;

}
