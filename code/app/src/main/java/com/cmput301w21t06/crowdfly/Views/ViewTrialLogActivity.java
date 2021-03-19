
//this class displays trials within an experiment and has other functionalities

package com.cmput301w21t06.crowdfly.Views;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

import com.cmput301w21t06.crowdfly.Controllers.ExperimentLog;
import com.cmput301w21t06.crowdfly.Controllers.SubscriptionManager;
import com.cmput301w21t06.crowdfly.Controllers.TrialAdapter;
import com.cmput301w21t06.crowdfly.Controllers.TrialLog;
import com.cmput301w21t06.crowdfly.Database.CrowdFlyFirestore;
import com.cmput301w21t06.crowdfly.Models.BinomialTrial;
import com.cmput301w21t06.crowdfly.Models.CountTrial;
import com.cmput301w21t06.crowdfly.Models.Experiment;
import com.cmput301w21t06.crowdfly.Models.MeasurementTrial;
import com.cmput301w21t06.crowdfly.Models.NewTrial;
import com.cmput301w21t06.crowdfly.Models.Trial;
import com.cmput301w21t06.crowdfly.Models.User;
import com.cmput301w21t06.crowdfly.R;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;

public class ViewTrialLogActivity extends AppCompatActivity implements
        EditBinomialTrialFragment.OnFragmentInteractionListener,
        EditCountTrialFragment.OnFragmentInteractionListener,
        EditMeasureTrialFragment.OnFragmentInteractionListener,
        CrowdFlyFirestore.OnDoneGetTrialsListener,
        SubscriptionManager.OnDoneGetSubscribedListener,
        CrowdFlyFirestore.OnDoneGetExpListener,
        CrowdFlyFirestore.OnDoneGetUserListener
{
    public static final String EXPERIMENT_IS_NO_LONGER_ACTIVE = "This experiment is no longer active.";
    private static ArrayList<Trial> trialArrayList = new ArrayList<Trial>();
    private ListView listView;
    private Button addButton;
    private Button questionButton;
    private Button qrButton;
    private Button subButton;
    private Button endButton;
    static Integer counter = 0;
    static int entry_pos;
    public TrialAdapter adapter;
    static public String trialType;
    static public String expID;
    private TrialLog trialLog;
    private Boolean subscribed = false;
    private Experiment currentExperiment;
    private User currentUser;
    private Boolean isOwner = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_trial_log);
        endButton = findViewById(R.id.endButton);
        //only update the trialtype once per experiment
        if (counter < 1){
            trialType =  getIntent().getStringExtra("trialType");
            expID = getIntent().getStringExtra("expID");
        }

        trialLog = TrialLog.getTrialLog();
        new CrowdFlyFirestore().getExperimentData(expID, this);
        new CrowdFlyFirestore().getUserProfile(FirebaseAuth.getInstance().getUid(), this);
        //setup the data
        setupData();
        setUpList();

        trialArrayList = trialLog.getTrials();

        questionButton = findViewById(R.id.questionButton);
        questionButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ViewTrialLogActivity.this, ViewQuestionLogActivity.class);
                startActivity(intent);
            }
        });
        qrButton = findViewById(R.id.QRButton);
        addButton = findViewById(R.id.addButton);
        questionButton = findViewById(R.id.questionButton);
        subButton = findViewById(R.id.subButton);
        subButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentExperiment!=null && currentUser != null){
                    if( !subscribed){
                        currentExperiment.subscribe(currentUser);
                        currentExperiment.isSubscribed(currentUser, ViewTrialLogActivity.this);
                    }
                    else {
                        currentExperiment.unsubscribe(currentUser);
                        currentExperiment.isSubscribed(currentUser, ViewTrialLogActivity.this);
                    }
                }
            }
        });
        endButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(currentExperiment!=null && currentUser != null){
                    if(isOwner){
                        if(!currentExperiment.getStillRunning()){
                            currentExperiment.setStillRunning(true);
                        }
                        else {
                            currentExperiment.setStillRunning(false);
                        }
                        new CrowdFlyFirestore().setExperimentData(currentExperiment);
                    }
                    else {
                        makeToast("You must be the owner to end or start this experiment!");
                    }
                }
            }
        });
        questionButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ViewTrialLogActivity.this, ViewQuestionLogActivity.class);
                startActivity(intent);
            }
        });


        //add trials
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!currentExperiment.getStillRunning()){
                    makeToast(EXPERIMENT_IS_NO_LONGER_ACTIVE);
                    return;
                }
                if(subscribed || isOwner){

                    counter += 1;
                    Intent intent = new Intent(getApplicationContext(), NewTrial.class);
                    intent.putExtra("trialType", trialType);
                    intent.putExtra("expID", String.valueOf(expID));
                    startActivity(intent);
                }
                else {
                    makeToast("Please subscribe to the experiment to add trials");
                }

            }
        });



        //delete trials
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if(!currentExperiment.getStillRunning()){
                    makeToast(EXPERIMENT_IS_NO_LONGER_ACTIVE);
                    return false;
                }
                if(subscribed || isOwner) {
                    Trial btrial = (Trial) parent.getAdapter().getItem(position);
                    String trialIDAtPos = btrial.getTrialID();
                    new CrowdFlyFirestore().removeTrialData(expID, trialIDAtPos);
                    trialLog.removeTrial(position);
                    adapter.notifyDataSetChanged();
                }
                else {
                    makeToast("Please subscribe to the experiment to remove trials");
                }
                return false;
            }
        });

        //edit trials
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if(!currentExperiment.getStillRunning()){
                    makeToast(EXPERIMENT_IS_NO_LONGER_ACTIVE);
                    return;
                }
                if(subscribed || isOwner) {

                    Trial x = trialLog.getTrial(i);
                    if (trialType.equals("binomial")) {
                        EditBinomialTrialFragment editBinomialTrialFragment = new EditBinomialTrialFragment();
                        entry_pos = i;
                        Trial btrial = (Trial) adapterView.getAdapter().getItem(i);
                        String trialIDAtPos = btrial.getTrialID();
                        BinomialTrial trial = new CrowdFlyFirestore().getBTrial(expID, trialIDAtPos);
                        editBinomialTrialFragment.newInstance(trial).show(getSupportFragmentManager(), "EDIT TEXT");

                    }
                    if (trialType.equals("count")) {
                        EditCountTrialFragment editCountTrialFragment = new EditCountTrialFragment();
                        entry_pos = i;

                        Trial ctrial = (Trial) adapterView.getAdapter().getItem(i);
                        String trialIDAtPos = ctrial.getTrialID();
                        CountTrial trial = new CrowdFlyFirestore().getCTrial(expID, trialIDAtPos);
                        editCountTrialFragment.newInstance(trial).show(getSupportFragmentManager(), "EDIT TEXT");

                    }
                    if (trialType.equals("measurement")) {
                        EditMeasureTrialFragment editMeasureTrialFragment = new EditMeasureTrialFragment();
                        entry_pos = i;
                        Trial mtrial = (Trial) adapterView.getAdapter().getItem(i);
                        String trialIDAtPos = mtrial.getTrialID();
                        MeasurementTrial trial = new CrowdFlyFirestore().getMTrial(expID, trialIDAtPos);
                        editMeasureTrialFragment.newInstance(trial).show(getSupportFragmentManager(), "EDIT TEXT");

                    }
                }
                else {
                    makeToast("Please subscribe to the experiment to edit trials");
                }
                }


        });

    }

    private void makeToast(String s) {
        Toast.makeText(ViewTrialLogActivity.this, s, Toast.LENGTH_LONG).show();
    }

    private void setupData(){


        // get all experiment data from firestore
        new CrowdFlyFirestore().getTrialData(expID, this);


    }
    private void setUpList(){
        listView = findViewById(R.id.trialListView);
        adapter = new TrialAdapter(getApplicationContext(), 0, trialLog.getTrials(),trialType,expID);
        listView.setAdapter(adapter);
    }

    @Override
    public void onOkPressed(BinomialTrial btrial){
        this.trialLog.set(entry_pos, btrial);
        setUpList();
    }


    @Override
    public void onOkPressed(CountTrial ctrial) {
        this.trialLog.set(entry_pos, ctrial);
        setUpList();

    }

    @Override
    public void onOkPressed(MeasurementTrial mtrial) {
        this.trialLog.set(entry_pos, mtrial);
        setUpList();
    }


    @Override
    public void onDoneGetTrials(TrialLog trialLog) {
        this.trialLog = trialLog;
        trialArrayList = trialLog.getTrials();
        adapter.clear();
        adapter.addAll(trialArrayList);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onDoneGetIsSubscribed(Boolean result) {
        this.subscribed = result;
        if(result) {
            subButton.setText(R.string.unsubscribe);
        }
        else {
            subButton.setText(R.string.subscribe);
        }
    }

    @Override
    public void onDoneGetUser(User user) {
        this.currentUser = user;
        this.currentExperiment.isSubscribed(user, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupData();
    }

    @Override
    public void onDoneGetExperiment(Experiment experiment) {
        this.currentExperiment = experiment;
        this.isOwner = this.currentExperiment.getOwnerID().equals(FirebaseAuth.getInstance().getUid());
        if(currentExperiment.getStillRunning()){

            endButton.setText("End");
        }
        else{
            endButton.setText("Start");
        }
    }
}