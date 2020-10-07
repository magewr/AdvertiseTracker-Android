package com.magewr.advertisetracker.advertisetracker;


import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

public class AdvertiseTracker {

    // 스크롤 상태에 따라 Observable에 발행하기 위한 enum
    private enum State {
        Scroll,
        Idle,
    }

    // 광고 타입
    public enum ADType {
        HomeBigBanner("홈_메인배너"),
        HomeSmallBanner("홈_띠배너");

        private String typeName;

        ADType(String typeName) {
            this.typeName = typeName;
        }

        /**
         * PV, UV 등 FA View Event 전송용 스트링
         * @param eventName 이벤트명
         * @param isUnique 유니크 여부
         * @return 스트링
         */
        public String getViewEventFullString(String eventName, boolean isUnique) {
            String eventString;
            eventString = String.format("%s_%s_%s", this.typeName, isUnique ? "SV" : "PV", eventName);

            return eventString.replace(" ", "_");
        }

        /**
         * PC, UC 등 FA Click Event 전송용 스트링
         * @param eventName 이벤트명
         * @param isUnique 유니크여부
         * @return 스트링
         */
        public String getClickEventFullString(String eventName, boolean isUnique) {
            String eventString;
            eventString = String.format("%s_%s_%s", this.typeName, isUnique ? "SC" : "C", eventName);

            return eventString.replace(" ", "_");
        }
    }

    // session Event 저장용 Set, 세션만료까지 유지이므로 static
    private static Set<String> sessionEventSet = new HashSet<>();
    // Page Event 저장용 Set, 필요시 초기화 가능
    private Set<String> pageEventSet = new HashSet<>();

    // DataSource Interface
    private EventNameDataSource eventNameDataSource;
    private AdvertiseTrackerDataSource advertiseTrackerDataSource;

    public void setEventNameDataSource(EventNameDataSource eventNameDataSource) {
        this.eventNameDataSource = eventNameDataSource;
    }

    public void setAdvertiseTrackerDataSource(AdvertiseTrackerDataSource advertiseTrackerDataSource) {
        this.advertiseTrackerDataSource = advertiseTrackerDataSource;
    }


    // 부모 스크롤뷰에 여러개의 리스너가 달려야 하므로 스크롤 리스너를 래핑
    private List<ScrollListener> scrollListenerList = new ArrayList<>();
    private NestedScrollView.OnScrollChangeListener parentScrollViewScrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
        for (ScrollListener listener : scrollListenerList) {
            listener.onScroll();
        }
    };

    ///////////////////////////////////////////////////
    // 광고 추적 public method
    ///////////////////////////////////////////////////

    /**
     * 스크롤 주체가 RecyclerView 이고 광고가 부모 RecylerView 또는 자식 RecyclerView 뷰홀더로 제공될 경우 사용하는 메소드 - Nested 가능
     * 스크롤 주체부터 광고 뷰홀더까지 단일 혹은 중첩 RecyclerView 로만 구성된 경우 사용
     *
     * @param type ADType
     * @param adListView 스크롤 주체가 되는 부모 RecyclerView
     * @param adViewHolderClass 광고 컨텐츠가 있는 뷰홀더 클래스
     * @return Observable - 프래그먼트나 액티비티에서 트래킹 생명주기를 관리하기 위해 Observable로 리턴
     */
    public Disposable addAdTrackingTypeListInScroll(ADType type, RecyclerView adListView, Class<? extends RecyclerView.ViewHolder> adViewHolderClass) {
        // 스크롤이벤트 발행하는 서브젝트
        PublishSubject<State> scrollSubject = PublishSubject.create();

        // 스크롤 이벤트를 rx로 발행 (rxbinding에서 연속된 이벤트에서 누락이 있어서 수동으로 발행)
        RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {

            // RecyclerView가 스크롤 주체일 경우 ScrollState로 판별 가능하므로 IDLE만 추적
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE)
                    scrollSubject.onNext(State.Idle);
            }
        };
        adListView.addOnScrollListener(scrollListener);

        // resume되었을 경우 최초 1회 발행하기 위한 observable : 이벤트 구독된 이후에는 발행안됨
        final Observable<State> startTimer = Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(x -> State.Idle)
                .takeUntil(scrollSubject);

        return scrollSubject
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())     // 백그라운드 쓰레드에서 작업
                .mergeWith(startTimer)          // resume된경우 최초 1회 발행
                .flatMap(state -> getBannerViewHolder(adListView, adViewHolderClass))
                .doOnDispose(() -> scrollListenerList.remove(scrollListener)) // dispose시 리스너 해제
                .subscribe(viewHolder -> {
                            Rect scrollBounds = new Rect();
                            adListView.getHitRect(scrollBounds);

                            if (!viewHolder.itemView.getLocalVisibleRect(scrollBounds) || scrollBounds.height() < viewHolder.itemView.getHeight()) {
                                // 뷰가 부분적으로 보일 경우
                            } else {
                                // 뷰가 전부 보일 경우
                                String eventName = eventNameDataSource.getEventName(type, viewHolder.getAdapterPosition(), viewHolder);
                                sendFAViewEvent(type, eventName);
                            }
                        }, error -> {
                        }
                );
    }

    /**
     * 스크롤 주체가 RecyclerView 이고 광고가 ViewPager 로 제공될 경우 사용하는 메소드 - Nested 가능
     * 스크롤 주체 RecyclerView 에서 자식 ViewHolder 안에 ViewPager 가 존재하는 경우 사용
     *
     * @param type ADType
     * @param adListView 스크롤 주체가 되는 부모 RecyclerView
     * @param adViewHolderClass 광고 ViewPager 가 있는 뷰홀더 클래스
     * @return Observable - 프래그먼트나 액티비티에서 트래킹 생명주기를 관리하기 위해 Observable로 리턴
     */
    public Disposable addAdTrackingPagerInList(ADType type, RecyclerView adListView, Class<? extends RecyclerView.ViewHolder> adViewHolderClass) {
        // 스크롤이벤트 발행하는 서브젝트
        PublishSubject<State> scrollSubject = PublishSubject.create();

        List<ViewPager> adViewPagerList = new ArrayList<>();

        // 스크롤 이벤트를 rx로 발행 (rxbinding에서 연속된 이벤트에서 누락이 있어서 수동으로 발행)
        RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener() {

            // RecyclerView가 스크롤 주체일 경우 ScrollState로 판별 가능하므로 IDLE만 추적
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE)
                    scrollSubject.onNext(State.Idle);
            }
        };
        adListView.addOnScrollListener(scrollListener);

        // 리사이클러뷰 안의 뷰페이저는 나중에 그려지므로 리스너만 미리 만들어둠
        ViewPager.OnPageChangeListener pagerScrollListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE)
                    scrollSubject.onNext(State.Idle);
            }
        };

        // resume되었을 경우 최초 1회 발행하기 위한 observable : 이벤트 구독된 이후에는 발행안됨
        final Observable<State> startTimer = Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(x -> State.Idle)
                .takeUntil(scrollSubject);

        return scrollSubject.throttleFirst(100, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())// 백그라운드 쓰레드에서 작업
                .mergeWith(startTimer)          // resume된경우 최초 1회 발행
                .flatMap(state -> getBannerViewHolder(adListView, adViewHolderClass))   // 배너가 있는 뷰홀더 가져옴
                .flatMap(viewHolder -> getBannerViewPager((ViewGroup) viewHolder.itemView)) // 뷰홀더에서 배너 ViewPager 가져옴
                .map(viewPager -> {
                    // 리스너 등록, 추후 리스너 삭제 위해서 리스트에 담아둠
                    if (!adViewPagerList.contains(viewPager)) {
                        viewPager.addOnPageChangeListener(pagerScrollListener);
                        adViewPagerList.add(viewPager);
                    }
                    return viewPager;
                })
                .doOnDispose(() -> {
                    // 리스너 일괄 해제
                    scrollListenerList.remove(scrollListener);
                    for (ViewPager viewPager : adViewPagerList) {
                        if (viewPager != null)
                            viewPager.removeOnPageChangeListener(pagerScrollListener);
                    }

                })
                .subscribe(viewPager -> {
                            Rect scrollBounds = new Rect();
                            adListView.getHitRect(scrollBounds);

                            if (!viewPager.getLocalVisibleRect(scrollBounds) || scrollBounds.height() < viewPager.getHeight()) {
                                // 뷰가 부분적으로 보일 경우
                            } else {
                                // 뷰가 전부 보일 경우
                                String eventName = eventNameDataSource.getEventName(type, viewPager.getCurrentItem(), null);
                                String subCategory = null;
                            }
                        }, error -> {
                        }
                );
    }

    /**
     * 스크롤 주체가 NestedScrollView 이고 자식 RecyclerView 안에 광고 뷰홀더가 존재하는 경우 (홈화면 하단 이벤트배너)
     *
     * @param type ADType
     * @param parentScrollView 부모 스크롤뷰
     * @param adListView 실제 광고가 있는 리싸이클러뷰
     * @param adViewHolderClass 리싸이클러뷰에서 광고가 담기는 뷰홀더 클래스
     * @return Observable - 프래그먼트나 액티비티에서 트래킹 생명주기를 관리하기 위해 Observable로 리턴
     */
    public Disposable addAdTrackingTypeListInScroll(ADType type, NestedScrollView parentScrollView, RecyclerView adListView, Class<? extends RecyclerView.ViewHolder> adViewHolderClass) {
        // 스크롤 주체가 NestedScrollView의 경우에는 ScrollState로 판별이 불가능하므로
        // onScroll에서 처리, 이 경우에는 Scroll이 멈춘 상태를 인지할 수 없으므로
        // 추가로 타이머 설정 - 이벤트 발행 후 타이머 틱 발생 전까지 이벤트가 발행되지 않은 경우 스탑으로 인지


        // 스크롤 도중일경우의 플래그
        AtomicBoolean isScroll = new AtomicBoolean(true);
        PublishSubject<State> scrollSubject = PublishSubject.create();

        // 스크롤이 발생하면 스크롤 이벤트 발행행
        // 스크롤 멈춤을 인지못하므로 여기서는 항상 스크롤 이벤트 발행
        ScrollListener scrollListener = () -> {
            isScroll.set(true);
            scrollSubject.onNext(State.Scroll);
        };
        scrollListenerList.add(scrollListener);
        parentScrollView.setOnScrollChangeListener(parentScrollViewScrollListener);


        // resume 체크용 타이머
        final Observable<State> startTimer = Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(x -> State.Idle)
                .takeUntil(scrollSubject);

        // 스크롤 스톱 인지용 타이머
        final Observable<State> scrollTimer = Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(x -> State.Idle)
                .takeUntil(scrollSubject)
                .repeat();

        return scrollSubject.subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .mergeWith(startTimer)
                .mergeWith(scrollTimer)
                .filter(state -> isScroll.get() && state == State.Idle)
                // 위 필터에 걸린 경우는 스크롤중이었는데 300ms마다 발행되는 Idle 이벤트가 가장 마지막으로 찍힌 경우이므로 스크롤이 진행하다 멈춘 것으로 판단
                .map(state -> {
                    isScroll.set(false);
                    return state;
                })
                .flatMap(state -> getCreatedChildViewFromRecyclerView(adListView))
                // 스크롤이 멈춘 상태이므로 현재 보여지는 자식뷰들을 가져와서 처리
                .doOnDispose(() -> scrollListenerList.remove(scrollListener)) // dispose시 리스너 해제
                .subscribe(viewHolder -> {
                            // 실제 광고가 담겨있는 뷰홀더일 경우만 처리
                            if (viewHolder.getClass() == adViewHolderClass) {
                                Rect scrollBounds = new Rect();
                                parentScrollView.getHitRect(scrollBounds);

                                if (!viewHolder.itemView.getLocalVisibleRect(scrollBounds) || scrollBounds.height() < viewHolder.itemView.getHeight()) {
                                    // 뷰가 부분적으로 보일 경우
                                } else {
                                    // 뷰가 전부 보일 경우
                                    String eventName = eventNameDataSource.getEventName(type, adListView.getChildAdapterPosition(viewHolder.itemView), viewHolder);
                                    sendFAViewEvent(type, eventName);
                                }
                            }
                        }, error -> {

                        }
                );
    }

    /**
     * 스크롤 주체가 NestedScrollView 이고 자식 ViewPager 안에 광고가 있는 경우 (홈화면 롤링배너)
     *
     * @param type ADType : 광고 타입
     * @param parent 부모가 되는 스크롤뷰
     * @param adViewPager 실제 광고가 보이는 뷰페이저 (주의! ViewPager2 아님)
     * @return Observable - 프래그먼트나 액티비티에서 트래킹 생명주기를 관리하기 위해 Observable로 리턴
     */
    public Disposable addAdTrackingTypePagerInScroll(ADType type, NestedScrollView parent, ViewPager adViewPager) {
        // 스크롤뷰와 롤링배너 뷰페이저 모두 스크롤이 끝날 때 광고 추적

        // 스크롤뷰 스크롤 상태 체크용 플래그 - Default : true (최초 이벤트 발행 시 필터에 걸리기 위해)
        AtomicBoolean isScrollViewScroll = new AtomicBoolean(true);
        // 뷰페이저 스크롤 상태 체크용 플래그 - Default : false
        AtomicBoolean isPagerScroll = new AtomicBoolean(false);

        PublishSubject<State> scrollSubject = PublishSubject.create();

        ScrollListener scrollListener = () -> {
            isScrollViewScroll.set(true);
            scrollSubject.onNext(State.Scroll);
        };
        scrollListenerList.add(scrollListener);
        parent.setOnScrollChangeListener(parentScrollViewScrollListener);

        PublishSubject<State> pagerScrollSubject = PublishSubject.create();

        ViewPager.OnPageChangeListener pagerListener = new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {

            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager.SCROLL_STATE_IDLE) {
                    isPagerScroll.set(false);
                    pagerScrollSubject.onNext(State.Idle);
                }
                else {
                    isPagerScroll.set(true);
                    pagerScrollSubject.onNext(State.Scroll);
                }
            }
        };
        adViewPager.addOnPageChangeListener(pagerListener);

        final Observable<State> startTimer = Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(x -> State.Idle)
                .takeUntil(scrollSubject);

        final Observable<State> scrollTimer = Observable.interval(300, TimeUnit.MILLISECONDS)
                .map(x -> State.Idle)
                .takeUntil(scrollSubject)
                .repeat();

        Observable<Boolean> scrollObservable = scrollSubject.subscribeOn(Schedulers.io())
                .throttleFirst(100, TimeUnit.MILLISECONDS)
                .mergeWith(scrollTimer)
                .filter(state -> isScrollViewScroll.get() && state == State.Idle)
                .map(state -> {
                    isScrollViewScroll.set(false);
                    return isScrollViewScroll.get();
                });

        return Observable.combineLatest(
                scrollObservable,
                pagerScrollSubject.mergeWith(startTimer).subscribeOn(Schedulers.io()),
                (isScroll, isPagerState) -> {
                    // 스크롤뷰, 뷰페이저 모두 스크롤이 멈춰있을 경우에만 발행
                    if (!isScrollViewScroll.get() && !isPagerScroll.get())
                        return adViewPager.getCurrentItem();
                    else
                        return -1;
                })
                .observeOn(Schedulers.io())
                .filter(position -> position >= 0)
                .doOnDispose(() -> {
                    scrollListenerList.remove(scrollListener);
                    adViewPager.removeOnPageChangeListener(pagerListener);
                })
                .subscribe(position -> {
                    Rect scrollBounds = new Rect();
                    parent.getHitRect(scrollBounds);
                    if (!adViewPager.getLocalVisibleRect(scrollBounds) || scrollBounds.height() < adViewPager.getHeight()) {
                        // 뷰가 부분적으로 보일 경우
                    } else {
                        // 뷰가 전부 보일 경우
                        String eventName = eventNameDataSource.getEventName(type, position, null);
                        sendFAViewEvent(type, eventName);
                    }
                }, error -> {

                });
    }

    /**
     * 화면전환 시 PV클리어 위한 메소드
     */
    public void clearPageEventCount() {
        pageEventSet.clear();
    }

    /**
     * 세션만료 상황일 경우 세션 이벤트 클리어 (1시간 이후 재접속 시 세션만료)
     */
    public void clearSession() {
        sessionEventSet.clear();
    }




    ///////////////////////////////////////////////////
    // 내부 로직 private method
    ///////////////////////////////////////////////////

    /**
     * 리싸이클러뷰에서 현재 그려져있는 뷰홀더 리스트를 가져오는 메소드
     * @param adListView RecyclerView
     * @return ViewHolderObservable
     */
    private Observable<RecyclerView.ViewHolder> getCreatedChildViewFromRecyclerView(RecyclerView adListView) {
        LinearLayoutManager lm = (LinearLayoutManager)adListView.getLayoutManager();
        int start = lm.findFirstVisibleItemPosition();
        int end = lm.findLastVisibleItemPosition();

        // 화면에 다 안그려졌을 경우 예외처리
        if (start == -1 || end == -1)
            return Observable.never();

        List<RecyclerView.ViewHolder> viewHolderList = new ArrayList<>();
        for (int i = start; i <= end ; i++) {
            RecyclerView.ViewHolder child = adListView.findViewHolderForAdapterPosition(i);
            if (child != null)
                viewHolderList.add(child);
        }

        if (viewHolderList.isEmpty())
            return Observable.never();

        return Observable.fromIterable(viewHolderList);
    }

    /**
     * 부모 RecyclerView 와 모든 자식 RecyclerView 에서 광고가 있는 타겟 ViewHolder 를 모두 찾아 Observable 로 제공하는 메소드
     * @param parent 부모 RecyclerView
     * @param listViewHolderClass 광고 타겟 ViewHolder 클래스
     * @return 필터링된 Observable
     */
    private Observable<RecyclerView.ViewHolder> getBannerViewHolder(RecyclerView parent, Class<? extends RecyclerView.ViewHolder> listViewHolderClass) {
        List<RecyclerView.ViewHolder> viewHolderList = getViewHolderList(parent, listViewHolderClass);

        if (viewHolderList.isEmpty())
            return Observable.never();

        return Observable.fromIterable(viewHolderList);
    }

    /**
     * RecyclerView 에서 광고가 있는 타겟 ViewHolder 리스트로 받아오는 메소드, 재귀함수
     * @param parent 뷰홀더를 찾을 RecyclerView
     * @param listViewHolderClass 광고 타겟 ViewHolder 클래스
     * @return 타겟 ViewHolder List
     */
    private List<RecyclerView.ViewHolder> getViewHolderList(RecyclerView parent, Class<? extends RecyclerView.ViewHolder> listViewHolderClass) {
        LinearLayoutManager lm = (LinearLayoutManager)parent.getLayoutManager();
        List<RecyclerView.ViewHolder> viewHolderList = new ArrayList<>();
        List<RecyclerView> childRecyclerViewList;

        int start = lm.findFirstVisibleItemPosition();
        int end = lm.findLastVisibleItemPosition();

        // 화면에 다 안그려졌을 경우 예외처리
        if (start == -1 || end == -1)
            return viewHolderList;

        for (int i = start ; i <= end ; i++) {
            RecyclerView.ViewHolder holder = parent.findViewHolderForAdapterPosition(i);
            if (holder != null) {
                if (holder.getClass() == listViewHolderClass)
                    viewHolderList.add(holder);

                if (holder.itemView instanceof ViewGroup) {
                    ChildViewFinder<RecyclerView> recyclerViewFinder = new ChildViewFinder<>(RecyclerView.class);
                    childRecyclerViewList = recyclerViewFinder.findChildView((ViewGroup) holder.itemView);

                    for (RecyclerView childRecyclerView : childRecyclerViewList) {
                        viewHolderList.addAll(getViewHolderList(childRecyclerView, listViewHolderClass));
                    }
                }

            }
        }

        return viewHolderList;
    }

    /**
     * ViewGroup 모든 자식에 있는 ViewPager 리스트를 가져옴
     * @param parent ViewGroup
     * @return ViewPager Observable
     */
    private Observable<ViewPager> getBannerViewPager(ViewGroup parent) {
        ChildViewFinder<ViewPager> viewPagerFinder = new ChildViewFinder<>(ViewPager.class);
        List<ViewPager> viewPagerList = viewPagerFinder.findChildView(parent);

        if (viewPagerList.isEmpty())
            return Observable.never();

        return Observable.fromIterable(viewPagerList);
    }

    /**
     * FA Event 전송 메소드
     * @param type ADType
     * @param eventName EventName
     */
    public void sendFAViewEvent(ADType type, String eventName) {
        if (eventName == null || eventName.isEmpty())
            return;

        String uvEventString = type.getViewEventFullString(eventName, true);
        if (!sessionEventSet.contains(uvEventString)) {
            sessionEventSet.add(uvEventString);
            advertiseTrackerDataSource.getAdvertiseTracker().sendAdvertiseEvent(uvEventString);
        }
        String pvEventString = type.getViewEventFullString(eventName, false);
        if (!pageEventSet.contains(pvEventString)) {
            pageEventSet.add(pvEventString);
            advertiseTrackerDataSource.getAdvertiseTracker().sendAdvertiseEvent(pvEventString);
        }
    }

    public void sendFAClickEvent(ADType type, String eventName) {
        if (eventName == null || eventName.isEmpty())
            return;

        String uvEventString = type.getClickEventFullString(eventName, true);
        if (!sessionEventSet.contains(uvEventString)) {
            sessionEventSet.add(uvEventString);
            advertiseTrackerDataSource.getAdvertiseTracker().sendAdvertiseEvent(uvEventString);
        }
        String pvEventString = type.getClickEventFullString(eventName, false);
        if (!pageEventSet.contains(pvEventString)) {
            pageEventSet.add(pvEventString);
            advertiseTrackerDataSource.getAdvertiseTracker().sendAdvertiseEvent(pvEventString);
        }
    }


    // 스크롤 리스너 래핑용 인터페이스
    private interface ScrollListener {
        void onScroll();
    }

    /**
     * 자식 전체에서 특정 클래스의 View 를 찾아주는 파인더 클래스
     * @param <T> 찾을 View
     */
    private class ChildViewFinder<T extends View> {
        private Class<T> clazz;

        // instanceof 사용하기 위해서 생성시 T 클래스를 파라미터로 받아야 함
        private ChildViewFinder(Class<T> clazz) {
            this.clazz = clazz;
        }

        // 재귀로 하이라키 트리에서 해당 클래스의 자식을 모두 추출하는 메소드
        private List<T> findChildView(ViewGroup viewGroup) {
            List<T> viewList = new ArrayList<>();

            int childCount = viewGroup.getChildCount();
            for (int i = 0 ; i < childCount ; i++) {
                View childView = viewGroup.getChildAt(i);
                // 찾는 클래스의 경우 담고
                if (childView.getClass() == clazz) {
                    viewList.add((T) childView);
                }
                // 뷰그룹일 경우 재귀 호출
                if (childView instanceof ViewGroup) {
                    viewList.addAll(findChildView((ViewGroup) childView));
                }
            }

            return viewList;
        }
    }


    /**
     * DataSource Interface
     */

    // 이벤트명 제공받는 인터페이스
    public interface EventNameDataSource {
        String getEventName(ADType type, int position, @Nullable RecyclerView.ViewHolder bannerViewHolder);
    }

    // 서브카테고리가 가변일 경우 제공받는 인터페이스 (ex, "청담클래스_탭이름_PV_이벤트명 에서 '탭이름' 영역)
    public interface SubCategoryNameDataSource {
        String getSubCategoryName(ADType type, int position, @Nullable RecyclerView.ViewHolder bannerViewHolder);
    }

    // 애널리틱스 트래커 제공받는 인터페이스
    public interface AdvertiseTrackerDataSource {
        AdvertiseTrackerDelegate getAdvertiseTracker();
    }

    // 애널리틱스 트래커 인터페이스 이 인터페이스를 구현한 애널리틱스 트래커는 모두 사용가능
    public interface AdvertiseTrackerDelegate {
        void sendAdvertiseEvent(String advertiseName);
    }
}
