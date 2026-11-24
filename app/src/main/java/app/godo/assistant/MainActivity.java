package app.godo.assistant;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static app.godo.assistant.Ui.*;

/** Home screen: assistant-first launchpad (redesigned). */
public class MainActivity extends Screen implements ChatHub.Listener, Account.Listener {
    private ChatHub hub;
    private LinearLayout list;
    private FrameLayout navSlot;

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("update", false)) Updates.showSheet(this);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        hub = ChatHub.get(this);
        if (!Prefs.onboarded(this)) {
            if (new SetupState(this).count() >= 3) Prefs.setOnboarded(this, true);
            else startActivity(new Intent(this, OnboardingActivity.class));
        }
        if (state == null && getIntent().getBooleanExtra("update", false)) Updates.showSheet(this);
        if (getIntent().getBooleanExtra("askMic", false) && !Voice.micAllowed(this)) {
            Voice.askMic(this, 3);
        } else if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 2);
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.liquidBg(this));
        root.setFitsSystemWindows(true);

        LinearLayout column = column(this);
        ScrollView scroll = new ScrollView(this);
        list = column(this);
        list.setPaddingRelative(0, dp(this, 8), 0, dp(this, 100));
        scroll.addView(list);
        column.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        navSlot = new FrameLayout(this);
        column.addView(navSlot, full());
        root.addView(column);

        // Circular FAB (+ only) floating above the bottom nav
        FrameLayout fab = new FrameLayout(this);
        int fabSize = dp(this, 60);
        GradientDrawable fabBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{ACCENT, Theme.darken(ACCENT, 0.78f)});
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setStroke(dp(this, 1), 0x48FFFFFF);
        fab.setBackground(fabBg);
        fab.setElevation(dp(this, 10));
        ImageView fabIcon = icon(this, R.drawable.ms_add, ON_ACCENT, 28);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(this, 28), dp(this, 28), Gravity.CENTER);
        fab.addView(fabIcon, iconLp);
        fab.setOnClickListener(v -> newChatSheet());
        fab.setContentDescription(getString(R.string.main_new_chat));

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(fabSize, fabSize,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        fp.bottomMargin = dp(this, 112); // above the floating nav
        root.addView(fab, fp);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        hub.addListener(this);
        Waiting.listener = waitingChanged;
        Providers.refresh(this);
        render();
        Account.addListener(this);
        Account.CLAUDE.refresh(this);
        Account.CODEX.refresh(this);
        Account.OPENCODE.refresh(this);
        Account.AGY.refresh(this);
        Account.GROK.refresh(this);
    }

    private final Runnable waitingChanged = this::render;

    @Override
    public void onAccountChanged() {
        if (!isFinishing() && !isDestroyed()) render();
    }

    @Override
    protected void onPause() {
        hub.removeListener(this);
        if (Waiting.listener == waitingChanged) Waiting.listener = null;
        Account.removeListener(this);
        super.onPause();
    }

    @Override
    public void onChatsChanged() {
        render();
    }

    @Override
    public void onMessage(Sessions.Session ses, Sessions.Msg msg) {
    }

    private void render() {
        list.removeAllViews();
        navSlot.removeAllViews();
        navSlot.addView(bottomNav(this, NAV_HOME, hub.runningCount()));

        // ── Header: avatar + status dot + search ─────────────────────────────
        LinearLayout header = row(this);
        header.setPaddingRelative(dp(this, 20), dp(this, 20), dp(this, 20), 0);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // Large circular avatar
        int avatarSize = dp(this, 90);
        FrameLayout avatarFrame = new FrameLayout(this);
        GradientDrawable avatarBorder = new GradientDrawable();
        avatarBorder.setShape(GradientDrawable.OVAL);
        avatarBorder.setStroke(dp(this, 3), ACCENT);
        avatarBorder.setColor(0x00000000);
        avatarFrame.setBackground(avatarBorder);
        avatarFrame.setClipToOutline(true);
        avatarFrame.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        ImageView face = new ImageView(this);
        face.setImageResource(R.drawable.app_logo);
        face.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams faceLp = new FrameLayout.LayoutParams(
                avatarSize - dp(this, 6), avatarSize - dp(this, 6), Gravity.CENTER);
        avatarFrame.addView(face, faceLp);
        header.addView(avatarFrame, new LinearLayout.LayoutParams(avatarSize, avatarSize));

        // Spacer to push dot+search to the right
        header.addView(new View(this), weight1());

        // Green availability dot (no label, no IP)
        SetupState setup = new SetupState(this);
        boolean ready = setup.canRun();
        View dot = dot(this, ready ? OK : ACCENT, 10);
        LinearLayout.LayoutParams dotLp = wrap();
        dotLp.setMarginEnd(dp(this, 16));
        header.addView(dot, dotLp);

        // Search icon
        android.widget.ImageButton search = iconButton(this, R.drawable.ms_search, getString(R.string.search_title));
        search.setOnClickListener(v -> go(SearchActivity.class));
        header.addView(search, new LinearLayout.LayoutParams(dp(this, 44), dp(this, 44)));

        list.addView(header, full());

        // ── Greeting ─────────────────────────────────────────────────────────
        LinearLayout greeting = column(this);
        greeting.setPaddingRelative(dp(this, 20), dp(this, 20), dp(this, 20), 0);

        // Try to get user's name from account; fall back to app name
        String userName = Account.CLAUDE.username;
        if (userName == null || userName.isEmpty()) userName = Account.CODEX.username;
        if (userName == null || userName.isEmpty()) userName = getString(R.string.app_name);
        // Capitalize first letter only
        if (!userName.isEmpty()) userName = userName.substring(0, 1).toUpperCase(Locale.getDefault()) + userName.substring(1);

        TextView helloText = title(this, "Hello, " + userName + "!", 28);
        helloText.setTypeface(sans(this, 700));
        greeting.addView(helloText);

        TextView subText = text(this, "What would you like to do today?", 15, MUTED);
        LinearLayout.LayoutParams subLp = full();
        subLp.topMargin = dp(this, 4);
        greeting.addView(subText, subLp);

        list.addView(greeting, full());

        // ── Setup warning (keep if not ready) ────────────────────────────────
        if (!ready || setup.next() >= 0 && setup.next() < 3) {
            LinearLayout warn = row(this);
            warn.setPaddingRelative(dp(this, 16), dp(this, 14), dp(this, 12), dp(this, 14));
            warn.setBackground(outlined(this, ACCENT_SOFT, 20, Theme.withAlpha(ACCENT, 0x55), 1));
            warn.addView(icon(this, R.drawable.ms_build_circle, ACCENT_TEXT, 24));
            LinearLayout wt = column(this);
            wt.setPaddingRelative(dp(this, 12), 0, dp(this, 8), 0);
            wt.addView(title(this, getString(R.string.main_setup_title), 15));
            int n = setup.next();
            wt.addView(text(this, n >= 0 ? getString(R.string.main_setup_next, SetupState.step(n))
                    : getString(R.string.main_setup_next_accessibility), 13, MUTED));
            warn.addView(wt, weight1());
            warn.addView(icon(this, R.drawable.ms_chevron_right, MUTED, 22));
            warn.setOnClickListener(v -> go(SetupActivity.class));
            LinearLayout.LayoutParams wp = full();
            wp.leftMargin = wp.rightMargin = dp(this, 16);
            wp.topMargin = dp(this, 16);
            list.addView(warn, wp);
        }

        // ── Waiting cards (approvals/questions in bubble) ─────────────────────
        Bubble bubble = BuddyService.get() == null ? null : BuddyService.get().bubble();
        List<String[]> cards = bubble == null ? new ArrayList<>() : bubble.openCards();
        if (!cards.isEmpty()) {
            list.addView(sectionLabel(this, getString(R.string.waiting_title)), full());
            LinearLayout g = group(this);
            for (String[] c : cards) addToGroup(this, g, cardRow(c, bubble), 52);
            list.addView(g, inset(this));
        }

        // ── Quick Actions banner ──────────────────────────────────────────────
        LinearLayout qaBanner = row(this);
        qaBanner.setPaddingRelative(dp(this, 16), dp(this, 14), dp(this, 14), dp(this, 14));
        qaBanner.setGravity(Gravity.CENTER_VERTICAL);
        qaBanner.setBackground(outlined(this, CARD, 20, OUTLINE, 1));
        qaBanner.setElevation(dp(this, 1));

        // Lightning bolt icon in an amber circle
        FrameLayout qaIconFrame = new FrameLayout(this);
        GradientDrawable qaIconBg = new GradientDrawable();
        qaIconBg.setShape(GradientDrawable.OVAL);
        qaIconBg.setColor(ACCENT_SOFT);
        qaIconFrame.setBackground(qaIconBg);
        int qaIconSize = dp(this, 42);
        qaIconFrame.addView(icon(this, R.drawable.ms_auto_awesome, ACCENT, 22),
                new FrameLayout.LayoutParams(dp(this, 22), dp(this, 22), Gravity.CENTER));

        LinearLayout qaText = column(this);
        qaText.setPaddingRelative(dp(this, 14), 0, 0, 0);
        qaText.addView(title(this, "Quick Actions", 15));
        qaText.addView(text(this, "Access your most used actions", 13, MUTED));

        qaBanner.addView(qaIconFrame, new LinearLayout.LayoutParams(qaIconSize, qaIconSize));
        qaBanner.addView(qaText, weight1());
        qaBanner.addView(icon(this, R.drawable.ms_chevron_right, MUTED, 22));
        // Quick Actions banner tap → open sessions/tools for now
        qaBanner.setOnClickListener(v -> go(MonitorActivity.class));

        LinearLayout.LayoutParams qaBannerLp = full();
        qaBannerLp.leftMargin = qaBannerLp.rightMargin = dp(this, 16);
        qaBannerLp.topMargin = dp(this, 24);
        list.addView(qaBanner, qaBannerLp);

        // ── 4 action shortcut circles ─────────────────────────────────────────
        LinearLayout shortcuts = row(this);
        shortcuts.setGravity(Gravity.CENTER);
        shortcuts.setPaddingRelative(dp(this, 16), dp(this, 20), dp(this, 16), 0);

        Object[][] actions = {
                {R.drawable.ms_terminal,    "Run a Task",    (Runnable) () -> newChatWithHint("Run a task")},
                {R.drawable.ms_description, "Write Code",    (Runnable) () -> newChatWithHint("Write code")},
                {R.drawable.ms_forum,       "Browse Web",    (Runnable) () -> go(BrowserActivity.class)},
                {R.drawable.ms_image,       "Create Image",  (Runnable) () -> newChatWithHint("Create an image")},
        };

        for (int i = 0; i < actions.length; i++) {
            final int idx = i;
            LinearLayout item = column(this);
            item.setGravity(Gravity.CENTER_HORIZONTAL);

            // Circle button
            FrameLayout circle = new FrameLayout(this);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(ACCENT_SOFT);
            circle.setBackground(circleBg);
            circle.setBackgroundResource(ripple(this, true));
            int circleSize = dp(this, 60);
            circle.addView(icon(this, (Integer) actions[i][0], ACCENT, 26),
                    new FrameLayout.LayoutParams(dp(this, 26), dp(this, 26), Gravity.CENTER));
            circle.setOnClickListener(v -> ((Runnable) actions[idx][2]).run());

            TextView label = text(this, (String) actions[i][1], 12, TEXT2);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    dp(this, 72), ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(this, 8);
            labelLp.gravity = Gravity.CENTER_HORIZONTAL;

            item.addView(circle, new LinearLayout.LayoutParams(circleSize, circleSize));
            item.addView(label, labelLp);

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            shortcuts.addView(item, itemLp);
        }

        LinearLayout.LayoutParams shortcutsLp = full();
        shortcutsLp.topMargin = 0;
        list.addView(shortcuts, shortcutsLp);

        // ── Recent Chats (3 rows, text-only) ─────────────────────────────────
        List<Sessions.Session> all = new ArrayList<>();
        for (Sessions.Session ses : hub.sessions.all) {
            if (!ses.parent.isEmpty()) continue;
            if (!ses.messages.isEmpty() && !ses.archived) all.add(ses);
        }
        all.sort((a, b) -> Long.compare(b.updated, a.updated));
        List<Sessions.Session> recent = all.subList(0, Math.min(3, all.size()));

        LinearLayout rcHeader = row(this);
        rcHeader.setPaddingRelative(dp(this, 20), dp(this, 24), dp(this, 20), dp(this, 8));
        rcHeader.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout clockBox = new FrameLayout(this);
        clockBox.addView(icon(this, R.drawable.ms_schedule, TEXT, 22),
                new FrameLayout.LayoutParams(dp(this, 22), dp(this, 22), Gravity.CENTER));

        TextView rcTitle = title(this, "Recent Chats", 16);
        rcTitle.setPaddingRelative(dp(this, 10), 0, 0, 0);

        rcHeader.addView(clockBox, new LinearLayout.LayoutParams(dp(this, 22), dp(this, 22)));
        rcHeader.addView(rcTitle, weight1());

        TextView seeAll = text(this, "See all →", 14, ACCENT_TEXT);
        seeAll.setTypeface(sans(this, 500));
        seeAll.setOnClickListener(v -> go(SearchActivity.class));
        rcHeader.addView(seeAll, wrap());

        LinearLayout.LayoutParams rcHeaderLp = full();
        list.addView(rcHeader, rcHeaderLp);

        if (recent.isEmpty()) {
            LinearLayout empty = column(this);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            empty.setPaddingRelative(dp(this, 32), dp(this, 16), dp(this, 32), dp(this, 8));
            TextView et = text(this, getString(R.string.main_empty_text), 14, MUTED);
            et.setGravity(Gravity.CENTER);
            list.addView(et, full());
        } else {
            LinearLayout rcCard = new LinearLayout(this);
            rcCard.setOrientation(LinearLayout.VERTICAL);
            rcCard.setBackground(outlined(this, CARD, 20, OUTLINE, 1));

            for (int i = 0; i < recent.size(); i++) {
                Sessions.Session ses = recent.get(i);
                LinearLayout row = listRow(this);
                row.setPaddingRelative(dp(this, 20), dp(this, 16), dp(this, 20), dp(this, 16));
                TextView title = single(text(this, ses.label(), 15, TEXT));
                title.setTypeface(sans(this, 400));
                row.addView(title, weight1());
                row.setOnClickListener(v -> open(ses));
                rcCard.addView(row);
                // hairline divider between rows (not after last)
                if (i < recent.size() - 1) {
                    View divider = new View(this);
                    divider.setBackgroundColor(LINE);
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    dlp.setMarginStart(dp(this, 20));
                    rcCard.addView(divider, dlp);
                }
            }

            LinearLayout.LayoutParams rcCardLp = full();
            rcCardLp.leftMargin = rcCardLp.rightMargin = dp(this, 16);
            list.addView(rcCard, rcCardLp);
        }
    }

    // ── Helper: open a new chat pre-seeded with a hint ────────────────────────
    private void newChatWithHint(String hint) {
        // Open the new-chat sheet; the hint is a starting prompt direction
        newChatSheet();
    }

    private View cardRow(String[] card, Bubble bubble) {
        Sessions.Session ses = card[0] == null ? null : hub.sessions.find(card[0]);
        LinearLayout r = listRow(this);
        r.setPaddingRelative(dp(this, 14), dp(this, 14), dp(this, 14), dp(this, 14));
        r.addView(icon(this, "ask".equals(card[2]) ? R.drawable.ms_chat_bubble : R.drawable.ms_shield_person, ACCENT, 24));
        LinearLayout col = column(this);
        col.setPaddingRelative(dp(this, 14), 0, 0, 0);
        col.addView(single(title(this, ses != null ? ses.label() : card.length > 3 && card[3] != null ? card[3]
                : getString(R.string.main_card_other_session), 15)));
        TextView t = text(this, card[1].replace('\n', ' '), 14, MUTED);
        t.setMaxLines(2);
        t.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(t);
        r.addView(col, weight1());
        r.addView(icon(this, R.drawable.ms_chevron_right, MUTED, 22));
        r.setOnClickListener(v -> {
            bubble.bringForward(card[0], card[1]);
            if (ses != null) open(ses);
            else bubble.expand();
        });
        return r;
    }

    private void newChatSheet() {
        LinearLayout c = column(this);
        TextView h = title(this, getString(R.string.main_new_chat), 18);
        h.setPaddingRelative(0, 0, 0, dp(this, 8));
        c.addView(h);
        final Dialog[] d = new Dialog[1];
        final boolean[] dev = {Prefs.devMode(this)};
        LinearLayout sw = protect(switchRow(this, getString(R.string.chat_linux_tools),
                getString(R.string.chat_linux_tools_info), dev[0], on -> dev[0] = on));
        sw.setPaddingRelative(dp(this, 4), dp(this, 4), dp(this, 4), dp(this, 12));
        c.addView(sw, full());
        String def = Prefs.backend(this);
        for (Account acc : Account.ALL) {
            String[] a = {acc.agent, acc.name(), acc.tagline()};
            boolean ready = SetupState.signedIn(this, acc.agent);
            if (!ready) a[2] = getString(SetupState.installed(this, acc.agent) ? R.string.main_new_chat_not_signed_in
                    : R.string.main_new_chat_not_installed);
            LinearLayout r = row(this);
            r.setPaddingRelative(dp(this, 4), dp(this, 12), dp(this, 4), dp(this, 12));
            r.setBackgroundResource(ripple(this, false));
            if (!ready) r.setAlpha(0.45f);
            r.addView(agentTile(this, a[0]));
            LinearLayout t = column(this);
            t.setPaddingRelative(dp(this, 14), 0, 0, 0);
            LinearLayout tr = row(this);
            tr.addView(title(this, a[1], 15));
            if (a[0].equals(def)) {
                TextView tag = monoText(this, getString(R.string.main_new_chat_default), 11, ACCENT_TEXT);
                tag.setPaddingRelative(dp(this, 6), dp(this, 1), dp(this, 6), dp(this, 1));
                tag.setBackground(rounded(this, ACCENT_SOFT, 6));
                LinearLayout.LayoutParams tp = wrap();
                tp.setMarginStart(dp(this, 8));
                tr.addView(tag, tp);
            }
            if (acc.freeTier()) {
                LinearLayout.LayoutParams tp = wrap();
                tp.setMarginStart(dp(this, 8));
                tr.addView(freeTag(this), tp);
            }
            t.addView(tr);
            t.addView(text(this, a[2], 13, MUTED));
            r.addView(t, weight1());
            r.addView(icon(this, R.drawable.ms_chevron_right, MUTED, 22));
            r.setOnClickListener(v -> {
                d[0].dismiss();
                if (ready) open(hub.create(a[0], true, dev[0]));
                else startActivity(new Intent(this, SignInActivity.class).putExtra("agent", a[0]));
            });
            c.addView(r, full());
        }
        d[0] = bottomSheet(this, c);
        d[0].show();
    }

    static String ago(long t) {
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 60) return BuddyApp.str(R.string.main_ago_now);
        if (s < 3600) return BuddyApp.plural(R.plurals.main_ago_minutes, (int) (s / 60), s / 60);
        if (s < 86400) return BuddyApp.plural(R.plurals.main_ago_hours, (int) (s / 3600), s / 3600);
        return BuddyApp.plural(R.plurals.main_ago_days, (int) (s / 86400), s / 86400);
    }

    private void go(Class<?> c) {
        startActivity(new Intent(this, c));
    }

    private void open(Sessions.Session ses) {
        hub.select(ses);
        startActivity(new Intent(this, ChatActivity.class).putExtra("id", ses.id));
    }
}
