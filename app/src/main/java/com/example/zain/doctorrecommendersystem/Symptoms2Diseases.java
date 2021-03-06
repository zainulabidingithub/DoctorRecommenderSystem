package com.example.zain.doctorrecommendersystem;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.ibm.watson.developer_cloud.natural_language_understanding.v1.NaturalLanguageUnderstanding;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalysisResults;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.AnalyzeOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.EntitiesOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.Features;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsOptions;
import com.ibm.watson.developer_cloud.natural_language_understanding.v1.model.KeywordsResult;
import com.ibm.watson.developer_cloud.service.exception.BadRequestException;
import com.ibm.watson.developer_cloud.service.exception.ConflictException;
import com.ibm.watson.developer_cloud.service.exception.ForbiddenException;
import com.ibm.watson.developer_cloud.service.exception.ServiceResponseException;
import com.ibm.watson.developer_cloud.service.exception.UnsupportedException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.realm.Realm;

public class Symptoms2Diseases extends Fragment {

    Button button,history,clearText;
    Realm realm;

    // HashMap and ArrayList Declaration
    ArrayList<String> arrayList = new ArrayList<String>();
    Map<String, ArrayList<String>> hashMap = new HashMap<String,ArrayList<String>>();

    // For sorting purpose
    Map<Integer, String> treeMap = new TreeMap<Integer, String>(Collections.reverseOrder());

    // using in POSTaggerThread
    List extractedSymptoms_List = null;

    private static EditText editText;

    StringBuffer diseaseList_Output = new StringBuffer();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_symptoms2_diseases, container, false);

    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        realm = Realm.getDefaultInstance();
        button = (Button) getActivity().findViewById(R.id.showDisease);
        history = (Button) getActivity().findViewById(R.id.history);
        editText = (EditText)getActivity().findViewById(R.id.editText);
        clearText = (Button)getActivity().findViewById(R.id.clearText);
        LoadDatasetIntoHashmap();

        clearText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editText.setText("");
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                button.setEnabled(false);
                OnShowDiseaseClick();
            }
        });

        history.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(getContext(),searchHistory.class));
            }
        });

    }

    private void LoadDatasetIntoHashmap(){

        String data = "";

        InputStream is = this.getResources().openRawResource(R.raw.dataset);

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        if(is!=null){
            try{
                while ((data=reader.readLine()) != null){

                    String[] disease_symptoms_parts = data.split("\\:");

                    String disease = disease_symptoms_parts[0];
                    String symptoms = disease_symptoms_parts[1];

                    String[] symptoms_parts = symptoms.split("\\,");

                    arrayList.clear();

                    for(int i=0;i<symptoms_parts.length;i++){

                        arrayList.add(symptoms_parts[i]);

                    }

                    hashMap.put(disease, new ArrayList<String>(arrayList));

                }

                is.close();
            }catch (Exception e ){
                e.printStackTrace();
            }
        }

    }

    private void findDisease(String[] userInput_Parts){


        diseaseList_Output.setLength(0);
        treeMap.clear();

        for (Map.Entry<String, ArrayList<String>> entry : hashMap.entrySet()) {


            String Disease = entry.getKey();
            ArrayList<String> Symptoms = entry.getValue();

            Double Occurrence = 0.0;
            Double totalPercentage = 0.0;
            Double Percentage = 0.0;

            for(int i=0;i<userInput_Parts.length;i++) {

                //userInput_Parts[i]; " 1 symptoms at a time "

                Occurrence = 0.0;
                for(String Symptom : Symptoms){

                    Occurrence++;

                    // equalsIgnoreCase will check equal and ignore upper/lower cases
                    if(userInput_Parts[i].equalsIgnoreCase(Symptom)){

                        Percentage = Occurrence/(Symptoms.size()+1);
                        Percentage = 1 - Percentage;
                        totalPercentage = totalPercentage+Percentage;
                        Occurrence=0.0;
                        break;
                    }
                }

            }
            if(totalPercentage>0){

                int Hold;
                totalPercentage = totalPercentage*100;
                Hold = totalPercentage.intValue();
                treeMap.put(Hold,entry.getKey());

            }

        }

        int counter = 0;
        for(Map.Entry<Integer,String> entry : treeMap.entrySet()) {

            Integer key = entry.getKey();
            String value = entry.getValue();

            // Adding founded disease into stringBuffer with new line
            diseaseList_Output.append(value);
            diseaseList_Output.append(System.getProperty("line.separator"));

            counter++;

            if(counter > 4) {

                break;

            }
        }

        if(diseaseList_Output.length() != 0){

            button.setEnabled(true);
            Intent i = new Intent(getContext(),ShowDisease.class);
            i.putExtra("DiseaseNames", (Serializable) diseaseList_Output);

            realm.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(Realm bgRealm) {

                    searchHistoryJava OBJ = bgRealm.createObject(searchHistoryJava.class);
                    OBJ.setSymptoms(editText.getText().toString());
                    OBJ.setDiseases(String.valueOf(diseaseList_Output));

                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    Log.d("MainActivity","onSuccess on realm");
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(Throwable error) {
                    Log.d("MainActivity","onError on realm");
                }
            });

            startActivity(i);
        }else{

            openDialog_noDiseaseFound();
            button.setEnabled(true);

        }

    }

    private void openDialog_noDiseaseFound() {

        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View subView = inflater.inflate(R.layout.dialog_nodiseasefound, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("No Disease Found!");
        builder.setView(subView);

        builder.setPositiveButton("Got it!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                Toast.makeText(getActivity(),("\ud83d\ude01"), Toast.LENGTH_SHORT).show();

            }
        });

        builder.show();


    }

    private void userInput_POSTagger() {

        Thread POSTaggerThread = new Thread(new Runnable() {
            public void run() {

                try{

                    String UserInputSymptoms = editText.getText().toString();

                    extractedSymptoms_List = new ArrayList();

                    NaturalLanguageUnderstanding service = new NaturalLanguageUnderstanding(
                            NaturalLanguageUnderstanding.VERSION_DATE_2017_02_27,
                            "42278379-b450-410d-88aa-4a7c07b294c8",
                            "UmLXp7ucBukI"
                    );


                    EntitiesOptions entitiesOptions = new EntitiesOptions.Builder()
                            .emotion(true)
                            .sentiment(true)
                            .limit(2)
                            .build();

                    KeywordsOptions keywordsOptions = new KeywordsOptions.Builder()
                            .emotion(true)
                            .sentiment(true)
                            .limit(2)
                            .build();

                    Features features = new Features.Builder()
                            .entities(entitiesOptions)
                            .keywords(keywordsOptions)
                            .build();

                    AnalyzeOptions parameters = new AnalyzeOptions.Builder()
                            .text(UserInputSymptoms)
                            .features(features)
                            .build();

                    AnalysisResults response = service
                            .analyze(parameters)
                            .execute();
                    List<KeywordsResult> keywords_get = response.getKeywords();

                    for (KeywordsResult relation : keywords_get) {

                        System.out.println(relation.getText());
                        extractedSymptoms_List.add(relation.getText());

                    }

                }
                catch(Exception e){

                }
//
//                 catch (BadRequestException e){
//
//
//                 }catch(ServiceResponseException e){
//
//                     Log.i(e.getMessage(),"In Exception Bro");
//
//                 }

            }
        });

        POSTaggerThread.start();

        try {

            POSTaggerThread.join();
            String[] extractedSymptoms_array = new String[extractedSymptoms_List.size()];
            extractedSymptoms_List.toArray(extractedSymptoms_array);
            findDisease(extractedSymptoms_array);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void OnShowDiseaseClick(){


        if(TextUtils.isEmpty(editText.getText().toString()))
        {
            Toast.makeText(getContext(), " Enter Something in Text Field : ", Toast.LENGTH_LONG).show();
            button.setEnabled(true);
            return;

        }else{

            if(isNetworkAvailable()){

                userInput_POSTagger();

            }
            else {

                button.setEnabled(true);
                Toast.makeText(getContext(),"Network is unavailable",Toast.LENGTH_SHORT).show();

            }

        }

    }

    private boolean isNetworkAvailable() {

        ConnectivityManager manager = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkinfo = manager.getActiveNetworkInfo();
        boolean  isAvailable = false;
        if ( networkinfo != null && networkinfo.isConnected()){
            isAvailable = true;
        }
        return isAvailable;

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        realm.close();
    }
}