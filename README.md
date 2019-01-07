# Realm Useful Helpers
[ ![Download](https://api.bintray.com/packages/abou7mied/maven/realm-useful-helpers/images/download.svg?version=1.0.1) ](https://bintray.com/abou7mied/maven/realm-useful-helpers/1.0.1/link)

This library is inspired from [realmfieldnameshelper](https://github.com/cmelchior/realmfieldnameshelper) and added extra fetaures.

## Features:
- Auto generate helper classes that can help make Realm queries more type safe
-  Map Gson object field names to Realm field names and vice versa 


For each Realm model class a corresponding `<class>Fields` class is created with static references
to all queryable field names.

## Installation

Include the following dependency in your `gradle.build` file

```gradle
annotationProcessor 'com.github.abou7mied:realm-useful-helpers:1.0.1'
```

Add the maven repo to your repositories
```
repositories {
    maven {
        url "https://dl.bintray.com/abou7mied/maven"
    }
}
```

## Usage

The library adds an annotation processor that automatically detects all Realm model classes and
generated an extra class called `<className>Fields`. This class will contain static references
to all field names that can be queried.

```java
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
```

**PersonFields** class is automatically generated

```java
package io.realm.fields;

import java.lang.String;

public final class PersonFields {
  public static final String NAME = "name";

  public static final String HAS_DOGS = "hasDogs";

  public static final class FAVORITE_DOG {
    public static final String $ = "favoriteDog";

    public static final String NAME = "favoriteDog.name";
  }

  public static final class DOGS {
    public static final String $ = "dogs";

    public static final String NAME = "dogs.name";
  }
}
```

And can be used when creating queries

```java
Realm realm = Realm.getDefaultInstance();
RealmResults<Person> results = realm.where(Person.class)
                                    .equalTo(PersonFields.NAME, "John")
                                    .findAll();

RealmResults<Person> results = realm.where(Person.class)
                                    .equalTo(PersonFields.FAVORITE_DOG.NAME, "Fido")
                                    .findAll();
```

**RealmJsonMapping** class is generated  too

```java
Person person = new Person();
person.setName("John");
person.setHasDogs(false);

Gson gson = new Gson();
String jsonString = gson.toJson(person); // {"person_name":"John","has_dogs":false}
Log.d("jsonString", jsonString + "");

try {
    JSONObject personJson = new JSONObject(jsonString);
    final JSONObject mapped = RealmJsonMapping.mapJsonObjectToRealm(PersonFields.class, personJson); // {"name":"John","hasDogs":false}
    Log.d("mapped", mapped + "");
    Realm.getDefaultInstance().executeTransactionAsync(new Realm.Transaction() {
        @Override
        public void execute(Realm realm) {
            Realm.getDefaultInstance().createOrUpdateObjectFromJson(Person.class, mapped);
        }
    });
} catch (JSONException e) {
    e.printStackTrace();
}
```

## RealmJsonMapping Methods

`JSONObject mapJsonObjectToRealm(Class cls, JSONObject object)` mapping Gson field names of an object to Realm names

`JSONArray mapJsonArrayToRealm(Class cls, JSONArray array)` mapping Gson field names of all object in the array to Realm names


`String getRealmName(Class cls, String key)` get the corresponding realm field name of gson name









