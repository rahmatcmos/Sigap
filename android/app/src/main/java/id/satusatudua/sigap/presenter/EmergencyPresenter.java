/*
 * Copyright (c) 2015 SatuSatuDua.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package id.satusatudua.sigap.presenter;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import id.satusatudua.sigap.data.api.FirebaseApi;
import id.satusatudua.sigap.data.local.CacheManager;
import id.satusatudua.sigap.data.model.CandidateHelper;
import id.satusatudua.sigap.data.model.Case;
import id.satusatudua.sigap.data.model.User;
import id.satusatudua.sigap.data.model.UserLocation;
import id.satusatudua.sigap.util.RxFirebase;
import id.zelory.benih.presenter.BenihPresenter;
import id.zelory.benih.util.BenihScheduler;
import rx.Observable;
import timber.log.Timber;

/**
 * Created on : January 09, 2016
 * Author     : zetbaitsu
 * Name       : Zetra
 * Email      : zetra@mail.ugm.ac.id
 * GitHub     : https://github.com/zetbaitsu
 * LinkedIn   : https://id.linkedin.com/in/zetbaitsu
 */
public class EmergencyPresenter extends BenihPresenter<EmergencyPresenter.View> {

    private Case theCase;
    private List<CandidateHelper> candidateHelpers;
    private boolean trustedUserDone = false;
    private boolean findHelperDone = false;

    public EmergencyPresenter(View view) {
        super(view);
        theCase = CacheManager.pluck().getLastCase();
        candidateHelpers = new ArrayList<>();
        listenHelperStatus();
    }

    public void init() {
        if (theCase.getStatus() == Case.Status.BARU) {
            view.showLoading();
            addTrustedUser();
            findHelper();
        } else {
            loadHelper();
        }
    }

    private void sendData() {
        Map<String, Object> data = new HashMap<>();

        for (int i = 0; i < candidateHelpers.size(); i++) {
            Timber.d("Add helper: " + candidateHelpers.get(i).getCandidate().getName());
            //Simpan siapa saja yang didaftarkan untuk menolong kasus ini
            data.put("helperCases/" + theCase.getCaseId() + "/" + candidateHelpers.get(i).getCandidateId() + "/status/", "MENUNGGU");

            //Simpan kalau user ini pernah didaftarkan untuk menolong kasus ini
            data.put("userHelps/" + candidateHelpers.get(i).getCandidateId() + "/" + theCase.getCaseId() + "/status/", "MENUNGGU");
        }

        data.put("cases/" + theCase.getCaseId() + "/status", "BERJALAN");

        FirebaseApi.pluck().getApi().updateChildren(data);

        theCase.setStatus(Case.Status.BERJALAN);
        CacheManager.pluck().cacheLastCase(theCase);
    }

    //TODO get trusted user from sigap io
    public void addTrustedUser() {
        if (theCase.getStatus() == Case.Status.BARU) {
            RxFirebase.observeOnce(FirebaseApi.pluck().userTrusted(CacheManager.pluck().getCurrentUser().getUserId()))
                    .compose(BenihScheduler.pluck().applySchedulers(BenihScheduler.Type.IO))
                    .subscribe(userTrusted -> {
                        Timber.d("Trusted: " + userTrusted.toString());
                        trustedUserDone = true;
                        if (findHelperDone) {
                            sendData();
                        }
                        if (view != null) {
                            view.dismissLoading();
                        }
                    }, throwable -> {
                        Timber.e("Trusted error: " + throwable.getMessage());
                        if (view != null) {
                            view.dismissLoading();
                        }
                    });
        }
    }

    public void findHelper() {
        if (theCase.getStatus() == Case.Status.BARU) {
            List<UserLocation> nearbyUsers = CacheManager.pluck().getNearbyUsers();
            if (nearbyUsers == null) {
                view.showError("Tidak dapat menemukan pengguna lain disekitar anda.");
                view.dismissLoading();
                findHelperDone = true;
                if (trustedUserDone) {
                    sendData();
                }
            } else {
                Observable.from(nearbyUsers)
                        .compose(BenihScheduler.pluck().applySchedulers(BenihScheduler.Type.IO))
                        .flatMap(userLocation -> RxFirebase.observeOnce(FirebaseApi.pluck().users(userLocation.getUserId()))
                                .compose(BenihScheduler.pluck().applySchedulers(BenihScheduler.Type.IO))
                                .map(dataSnapshot -> dataSnapshot.getValue(User.class)))
                        .filter(user -> user.getStatus() == User.Status.SIAP)
                        .take(5)
                        .toList()
                        .doOnNext(users -> {
                            for (int i = 0; i < users.size(); i++) {
                                CandidateHelper candidateHelper = new CandidateHelper();
                                candidateHelper.setCandidateId(users.get(i).getUserId());
                                candidateHelper.setStatus(CandidateHelper.Status.MENUNGGU);
                                candidateHelper.setCandidate(users.get(i));

                                int x = candidateHelpers.indexOf(candidateHelper);
                                if (x >= 0) {
                                    candidateHelpers.set(x, candidateHelper);
                                } else {
                                    candidateHelpers.add(candidateHelper);
                                }
                            }

                            findHelperDone = true;
                            if (trustedUserDone) {
                                sendData();
                            }
                        })
                        .flatMap(users -> Observable.from(candidateHelpers))
                        .subscribe(candidateHelper -> {
                            if (view != null) {
                                view.onNewHelperAdded(candidateHelper);
                                view.dismissLoading();
                            }
                        }, throwable -> {
                            findHelperDone = true;
                            Timber.e("Error: " + throwable.getMessage());
                            if (view != null) {
                                view.showError(throwable.getMessage());
                                view.dismissLoading();
                            }
                        });
            }
        }
    }

    public void loadHelper() {
        Timber.d("Load helper");
        view.showLoading();
        RxFirebase.observeOnce(FirebaseApi.pluck().helperCases(theCase.getCaseId()))
                .compose(BenihScheduler.pluck().applySchedulers(BenihScheduler.Type.IO))
                .flatMap(dataSnapshot -> Observable.from(dataSnapshot.getChildren()))
                .doOnNext(dataSnapshot -> Timber.d("Data: " + dataSnapshot.toString()))
                .flatMap(dataSnapshot -> RxFirebase.observeOnce(FirebaseApi.pluck().users(dataSnapshot.getKey()))
                        .compose(BenihScheduler.pluck().applySchedulers(BenihScheduler.Type.IO))
                        .map(dataSnapshot1 -> dataSnapshot1.getValue(User.class))
                        .map(user -> {
                            CandidateHelper candidateHelper = new CandidateHelper();
                            candidateHelper.setCandidateId(user.getUserId());
                            candidateHelper.setStatus(CandidateHelper.Status.valueOf(dataSnapshot
                                                                                             .child("status")
                                                                                             .getValue()
                                                                                             .toString()));
                            candidateHelper.setCandidate(user);

                            int x = candidateHelpers.indexOf(candidateHelper);
                            if (x >= 0) {
                                candidateHelpers.set(x, candidateHelper);
                            } else {
                                candidateHelpers.add(candidateHelper);
                            }
                            return candidateHelper;
                        })
                )
                .subscribe(candidateHelper -> {
                    if (view != null) {
                        view.onNewHelperAdded(candidateHelper);
                        view.dismissLoading();
                    }
                }, throwable -> {
                    throwable.printStackTrace();
                    Timber.e("LoadHelper Error: " + throwable.getMessage());
                    if (view != null) {
                        view.showError(throwable.getMessage());
                        view.dismissLoading();
                    }
                });
    }

    private void listenHelperStatus() {
        RxFirebase.observeChildChanged(FirebaseApi.pluck().helperCases(theCase.getCaseId()))
                .compose(BenihScheduler.pluck().applySchedulers(BenihScheduler.Type.IO))
                .map(firebaseChildEvent -> firebaseChildEvent.snapshot)
                .map(dataSnapshot -> {
                    CandidateHelper candidateHelper = new CandidateHelper();
                    candidateHelper.setCandidateId(dataSnapshot.getKey());
                    candidateHelper.setStatus(CandidateHelper.
                            Status.valueOf(dataSnapshot.child("status").getValue().toString()));

                    int x = candidateHelpers.indexOf(candidateHelper);
                    if (x >= 0) {
                        candidateHelpers.get(x).setStatus(candidateHelper.getStatus());
                        return candidateHelpers.get(x);
                    }
                    return null;
                })
                .subscribe(candidateHelper -> {
                    if (candidateHelper != null && view != null) {
                        view.onHelperStatusChanged(candidateHelper);
                    }
                }, throwable -> {
                    Timber.e(throwable.getMessage());
                    if (view != null) {
                        view.showError("Gagal memuat status penolong!");
                    }
                });
    }


    @Override
    public void saveState(Bundle bundle) {
        bundle.putParcelableArrayList("helpers", (ArrayList<CandidateHelper>) candidateHelpers);
    }

    @Override
    public void loadState(Bundle bundle) {
        candidateHelpers = bundle.getParcelableArrayList("helpers");
        if (candidateHelpers == null) {
            if (view != null) {
                view.showError("Gagal memuat data penolong!");
            }
        } else {
            Observable.from(candidateHelpers)
                    .subscribe(candidateHelper -> {
                        if (view != null) {
                            view.onNewHelperAdded(candidateHelper);
                            view.dismissLoading();
                        }
                    }, throwable -> {
                        Timber.e("Error: " + throwable.getMessage());
                        if (view != null) {
                            view.showError(throwable.getMessage());
                            view.dismissLoading();
                        }
                    });
        }
    }

    public interface View extends BenihPresenter.View {
        void onNewHelperAdded(CandidateHelper candidateHelper);

        void onHelperStatusChanged(CandidateHelper candidateHelper);
    }
}