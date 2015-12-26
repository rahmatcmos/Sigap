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

package id.satusatudua.sigap.ui;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;

import com.skyfishjy.library.RippleBackground;

import butterknife.Bind;
import butterknife.OnClick;
import id.satusatudua.sigap.R;
import id.satusatudua.sigap.data.model.Case;
import id.satusatudua.sigap.data.model.User;
import id.satusatudua.sigap.presenter.NearbyPresenter;
import id.satusatudua.sigap.presenter.TombolPresenter;
import id.zelory.benih.ui.BenihActivity;
import timber.log.Timber;

/**
 * Created on : December 24, 2015
 * Author     : zetbaitsu
 * Name       : Zetra
 * Email      : zetra@mail.ugm.ac.id
 * GitHub     : https://github.com/zetbaitsu
 * LinkedIn   : https://id.linkedin.com/in/zetbaitsu
 */
public class TombolActivity extends BenihActivity implements NearbyPresenter.View,
        TombolPresenter.View {

    @Bind(R.id.ripple) RippleBackground rippleBackground;

    private NearbyPresenter nearbyPresenter;
    private ProgressDialog progressDialog;
    private TombolPresenter tombolPresenter;

    @Override
    protected int getResourceLayout() {
        return R.layout.activity_tombol;
    }

    @Override
    protected void onViewReady(Bundle savedInstanceState) {
        rippleBackground.startRippleAnimation();
        nearbyPresenter = new NearbyPresenter(this);
        tombolPresenter = new TombolPresenter(this);
    }

    @OnClick(R.id.button_emergency)
    public void onEmergency() {
        tombolPresenter.createCase();
    }

    @OnClick(R.id.button_main)
    public void startMain() {
        startActivity(new Intent(this, MainActivity.class));
    }

    @Override
    public void showNearbyUsers(User nearbyUsers) {
        Timber.d(nearbyUsers.toString());
    }

    @Override
    public void showError(String errorMessage) {
        Snackbar snackbar = Snackbar.make(rippleBackground, errorMessage, Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundResource(R.color.colorAccent);
        snackbar.show();
    }

    @Override
    public void showLoading() {
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Silahkan tunggu...");
        progressDialog.show();
    }

    @Override
    public void dismissLoading() {
        progressDialog.dismiss();
    }

    @Override
    public void onCaseCreated(Case theCase) {
        Timber.d("case created: " + theCase.toString());
    }

    @Override
    public void onChatRoomCreated(String caseId) {
        Timber.d("chat room created: " + caseId);
    }

    @Override
    public void onHelperFound(User user) {
        Timber.d("Alhamdulillah helper found " + user.toString());
    }
}
