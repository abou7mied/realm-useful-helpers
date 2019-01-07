package com.github.abou7mied.realm_useful_helpers_example;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.github.abou7mied.realm_useful_helpers_example.models.Person;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.fields.PersonFields;
import io.realm.fields.RealmJsonMapping;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Realm.init(this);
        RealmConfiguration config = new RealmConfiguration.Builder()
                .deleteRealmIfMigrationNeeded()
                .build();

        Realm.setDefaultConfiguration(config);


        Log.d("PersonFields.NAME", PersonFields.NAME);
        Log.d("PersonFields.HAS_DOGS", PersonFields.HAS_DOGS);

        Log.d("PersonFields.FAVORITE_DOG.$", PersonFields.FAVORITE_DOG.$);
        Log.d("PersonFields.FAVORITE_DOG.NAME", PersonFields.FAVORITE_DOG.NAME);

        Log.d("PersonFields.DOGS.$", PersonFields.DOGS.$);
        Log.d("PersonFields.DOGS.NAME", PersonFields.DOGS.NAME);

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


    }
}
