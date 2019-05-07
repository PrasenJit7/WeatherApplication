package com.github.bkhezry.weather.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.github.bkhezry.weather.R;
import com.github.bkhezry.weather.model.CityInfo;
import com.github.bkhezry.weather.model.currentweather.CurrentWeatherResponse;
import com.github.bkhezry.weather.model.daysweather.ListItem;
import com.github.bkhezry.weather.model.daysweather.MultipleDaysWeatherResponse;
import com.github.bkhezry.weather.model.db.CurrentWeather;
import com.github.bkhezry.weather.model.db.FiveDayWeather;
import com.github.bkhezry.weather.model.db.ItemHourlyDB;
import com.github.bkhezry.weather.model.fivedayweather.FiveDayResponse;
import com.github.bkhezry.weather.model.fivedayweather.ItemHourly;
import com.github.bkhezry.weather.service.ApiService;
import com.github.bkhezry.weather.ui.fragment.HourlyFragment;
import com.github.bkhezry.weather.ui.fragment.MultipleDaysFragment;
import com.github.bkhezry.weather.utils.ApiClient;
import com.github.bkhezry.weather.utils.AppUtil;
import com.github.bkhezry.weather.utils.Constants;
import com.github.bkhezry.weather.utils.DbUtil;
import com.github.bkhezry.weather.utils.MyApplication;
import com.github.pwittchen.prefser.library.rx2.Prefser;
import com.miguelcatalan.materialsearchview.MaterialSearchView;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.IAdapter;
import com.mikepenz.fastadapter.adapters.ItemAdapter;
import com.mikepenz.fastadapter.listeners.OnClickListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import butterknife.BindArray;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.objectbox.Box;
import io.objectbox.BoxStore;
import io.objectbox.android.AndroidScheduler;
import io.objectbox.query.Query;
import io.objectbox.reactive.DataObserver;
import io.objectbox.reactive.DataSubscriptionList;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

public class MainActivity extends AppCompatActivity {

  @BindView(R.id.recycler_view)
  RecyclerView recyclerView;
  @BindView(R.id.temp_text_view)
  AppCompatTextView tempTextView;
  @BindView(R.id.description_text_view)
  AppCompatTextView descriptionTextView;
  @BindView(R.id.humidity_text_view)
  AppCompatTextView humidityTextView;
  @BindArray(R.array.mdcolor_500)
  @ColorInt
  int[] colors;
  @BindArray(R.array.mdcolor_500_alpha)
  @ColorInt
  int[] colorsAlpha;
  @BindView(R.id.animation_view)
  LottieAnimationView animationView;
  @BindView(R.id.toolbar)
  Toolbar toolbar;
  @BindView(R.id.search_view)
  MaterialSearchView searchView;
  @BindView(R.id.city_name_text_view)
  AppCompatTextView cityNameTextView;
  @BindView(R.id.wind_text_view)
  AppCompatTextView windTextView;
  private FastAdapter<FiveDayWeather> mFastAdapter;
  private ItemAdapter<FiveDayWeather> mItemAdapter;
  private CompositeDisposable disposable = new CompositeDisposable();
  private String defaultLang = "en";
  private List<FiveDayWeather> fiveDayWeathers;
  private ApiService apiService;
  private FiveDayWeather todayWeatherCollection;
  private Prefser prefser;
  private Box<CurrentWeather> currentWeatherBox;
  private Box<FiveDayWeather> fiveDayWeatherBox;
  private Box<ItemHourlyDB> itemHourlyDBBox;
  private DataSubscriptionList subscriptions = new DataSubscriptionList();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    ButterKnife.bind(this);
    setSupportActionBar(toolbar);
    initSearchView();
    initValues();
    initRecyclerView();
    showStoredCurrentWeather();
    showStoredFiveDayWeather();
    checkStoredCityInfo();
  }

  private void showStoredCurrentWeather() {
    Query<CurrentWeather> query = DbUtil.getCurrentWeatherQuery(currentWeatherBox);
    query.subscribe(subscriptions).on(AndroidScheduler.mainThread())
        .observer(new DataObserver<List<CurrentWeather>>() {
          @Override
          public void onData(@NonNull List<CurrentWeather> data) {
            if (data.size() > 0) {
              CurrentWeather currentWeather = data.get(0);
              tempTextView.setText(String.format(Locale.getDefault(), "%.0f°", currentWeather.getTemp()));
              descriptionTextView.setText(currentWeather.getMain());
              animationView.setAnimation(AppUtil.getWeatherAnimation(currentWeather.getWeatherId()));
              animationView.playAnimation();
              humidityTextView.setText(String.format(Locale.getDefault(), "%d%%", currentWeather.getHumidity()));
              windTextView.setText(String.format(Locale.getDefault(), "%.0fkm/hr", currentWeather.getWindSpeed()));
            }
          }
        });
  }

  private void showStoredFiveDayWeather() {
    Query<FiveDayWeather> query = DbUtil.getFiveDayWeatherQuery(fiveDayWeatherBox);
    query.subscribe(subscriptions).on(AndroidScheduler.mainThread())
        .observer(new DataObserver<List<FiveDayWeather>>() {
          @Override
          public void onData(@NonNull List<FiveDayWeather> data) {
            if (data.size() > 0) {
              todayWeatherCollection = data.remove(0);
              mItemAdapter.clear();
              mItemAdapter.add(data);
            }
          }
        });
  }

  private void checkStoredCityInfo() {
    if (prefser.contains(Constants.CITY_INFO)) {
      CityInfo cityInfo = prefser.get(Constants.CITY_INFO, CityInfo.class, null);
      if (cityInfo != null) {
        requestWeather(cityInfo.getName());
        cityNameTextView.setText(String.format("%s, %s", cityInfo.getName(), cityInfo.getCountry()));
      }
    }
  }

  private void requestWeather(String cityName) {
    getCurrentWeather(cityName);
    getFiveDaysWeather(cityName);
  }

  private void initSearchView() {
    searchView.setVoiceSearch(false);
    searchView.setCursorDrawable(R.drawable.ic_action_action_search);
    searchView.setEllipsize(true);
    searchView.setOnQueryTextListener(new MaterialSearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        requestWeather(query);
        return false;
      }

      @Override
      public boolean onQueryTextChange(String newText) {
        return false;
      }
    });
  }

  private void initValues() {
    prefser = new Prefser(this);
    apiService = ApiClient.getClient(getApplicationContext()).create(ApiService.class);
    BoxStore boxStore = MyApplication.getBoxStore();
    currentWeatherBox = boxStore.boxFor(CurrentWeather.class);
    fiveDayWeatherBox = boxStore.boxFor(FiveDayWeather.class);
    itemHourlyDBBox = boxStore.boxFor(ItemHourlyDB.class);
  }

  private void getCurrentWeather(String cityName) {
    disposable.add(
        apiService.getCurrentWeather(
            cityName, Constants.UNITS, defaultLang, Constants.APP_ID)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<CurrentWeatherResponse>() {
              @Override
              public void onSuccess(CurrentWeatherResponse currentWeatherResponse) {
                storeCurrentWeather(currentWeatherResponse);
                storeCityInfo(currentWeatherResponse);
              }

              @Override
              public void onError(Throwable e) {
                try {
                  HttpException error = (HttpException) e;
                  Log.e("MainActivity", "onError: " + e.getMessage());
                } catch (Exception exception) {
                  e.printStackTrace();
                }
              }
            })

    );
  }

  private void storeCurrentWeather(CurrentWeatherResponse response) {
    CurrentWeather currentWeather = new CurrentWeather();
    currentWeather.setTemp(response.getMain().getTemp());
    currentWeather.setHumidity(response.getMain().getHumidity());
    currentWeather.setDescription(response.getWeather().get(0).getDescription());
    currentWeather.setMain(response.getWeather().get(0).getMain());
    currentWeather.setWeatherId(response.getWeather().get(0).getId());
    currentWeather.setWindDeg(response.getWind().getDeg());
    currentWeather.setWindSpeed(response.getWind().getSpeed());
    currentWeather.setStoreTimestamp(System.currentTimeMillis());
    if (!currentWeatherBox.isEmpty()) {
      currentWeatherBox.removeAll();
      currentWeatherBox.put(currentWeather);
    } else {
      currentWeatherBox.put(currentWeather);
    }
  }

  private void storeCityInfo(CurrentWeatherResponse response) {
    CityInfo cityInfo = new CityInfo();
    cityInfo.setCountry(response.getSys().getCountry());
    cityInfo.setId(response.getId());
    cityInfo.setName(response.getName());
    prefser.put(Constants.CITY_INFO, cityInfo);
    cityNameTextView.setText(String.format("%s, %s", cityInfo.getName(), cityInfo.getCountry()));
  }

  private void getFiveDaysWeather(String cityName) {
    disposable.add(
        apiService.getMultipleDaysWeather(
            cityName, Constants.UNITS, defaultLang, 5, Constants.APP_ID)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<MultipleDaysWeatherResponse>() {
              @Override
              public void onSuccess(MultipleDaysWeatherResponse response) {
                handleFiveDayResponse(response, cityName);
              }

              @Override
              public void onError(Throwable e) {
                try {
                  HttpException error = (HttpException) e;
                  Log.e("MainActivity", "onError: " + e.getMessage());
                } catch (Exception exception) {
                  e.printStackTrace();
                }
              }
            })
    );
  }

  private void handleFiveDayResponse(MultipleDaysWeatherResponse response, String cityName) {
    fiveDayWeathers = new ArrayList<>();
    List<ListItem> list = response.getList();
    int day = 0;
    for (ListItem item : list) {
      Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
      Calendar newCalendar = AppUtil.addDays(calendar, day);
      FiveDayWeather fiveDayWeather = new FiveDayWeather();
      fiveDayWeather.setWeatherId(item.getWeather().get(0).getId());
      fiveDayWeather.setDt(item.getDt());
      fiveDayWeather.setMaxTemp(item.getTemp().getMax());
      fiveDayWeather.setMinTemp(item.getTemp().getMin());
      fiveDayWeather.setTemp(item.getTemp().getDay());
      fiveDayWeather.setColor(colors[day]);
      fiveDayWeather.setColorAlpha(colorsAlpha[day]);
      fiveDayWeather.setTimestampStart(AppUtil.getStartOfDayTimestamp(newCalendar));
      fiveDayWeather.setTimestampEnd(AppUtil.getEndOfDayTimestamp(newCalendar));
      fiveDayWeathers.add(fiveDayWeather);
      day++;
    }
    getFiveDaysHourlyWeather(cityName);
  }

  private void getFiveDaysHourlyWeather(String cityName) {
    disposable.add(
        apiService.getFiveDaysWeather(
            cityName, Constants.UNITS, defaultLang, Constants.APP_ID)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(new DisposableSingleObserver<FiveDayResponse>() {
              @Override
              public void onSuccess(FiveDayResponse response) {
                handleFiveDayHourlyResponse(response);
              }

              @Override
              public void onError(Throwable e) {
                Log.e("MainActivity", "onError: " + e.getMessage());
              }
            })

    );
  }

  private void handleFiveDayHourlyResponse(FiveDayResponse response) {
    if (!fiveDayWeatherBox.isEmpty()) {
      fiveDayWeatherBox.removeAll();
    }
    if (!itemHourlyDBBox.isEmpty()) {
      itemHourlyDBBox.removeAll();
    }
    for (FiveDayWeather fiveDayWeather : fiveDayWeathers) {
      long fiveDayWeatherId = fiveDayWeatherBox.put(fiveDayWeather);
      ArrayList<ItemHourly> listItemHourlies = new ArrayList<>(response.getList());
      for (ItemHourly itemHourly : listItemHourlies) {
        Calendar calendar = Calendar.getInstance(TimeZone.getDefault());
        calendar.setTimeInMillis(itemHourly.getDt() * 1000L);
        if (calendar.getTimeInMillis()
            <= fiveDayWeather.getTimestampEnd()
            && calendar.getTimeInMillis()
            > fiveDayWeather.getTimestampStart()) {
          ItemHourlyDB itemHourlyDB = new ItemHourlyDB();
          itemHourlyDB.setDt(itemHourly.getDt());
          itemHourlyDB.setFiveDayWeatherId(fiveDayWeatherId);
          itemHourlyDB.setTemp(itemHourly.getMain().getTemp());
          itemHourlyDB.setWeatherCode(itemHourly.getWeather().get(0).getId());
          itemHourlyDBBox.put(itemHourlyDB);
        }
      }
    }
  }


  private void initRecyclerView() {
    LinearLayoutManager layoutManager
        = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
    recyclerView.setLayoutManager(layoutManager);
    mItemAdapter = new ItemAdapter<>();
    mFastAdapter = FastAdapter.with(mItemAdapter);
    recyclerView.setItemAnimator(new DefaultItemAnimator());
    recyclerView.setAdapter(mFastAdapter);
    mFastAdapter.withOnClickListener(new OnClickListener<FiveDayWeather>() {
      @Override
      public boolean onClick(@Nullable View v, @NonNull IAdapter<FiveDayWeather> adapter, @NonNull FiveDayWeather item, int position) {
        HourlyFragment hourlyFragment = new HourlyFragment();
        hourlyFragment.setFiveDayWeather(item);
        AppUtil.showFragment(hourlyFragment, getSupportFragmentManager(), true);
        return true;
      }
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    disposable.dispose();
  }

  @OnClick(R.id.next_days_button)
  public void multipleDays() {
    AppUtil.showFragment(new MultipleDaysFragment(), getSupportFragmentManager(), true);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    MenuItem item = menu.findItem(R.id.action_search);
    searchView.setMenuItem(item);
    return true;
  }
}
