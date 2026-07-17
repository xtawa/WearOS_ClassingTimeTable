import { useEffect, useState } from "react";
import {
  ArrowRight,
  ArrowsClockwise,
  BellRinging,
  BracketsCurly,
  CalendarBlank,
  CalendarDots,
  CheckCircle,
  CloudArrowUp,
  DeviceMobile,
  GithubLogo,
  List,
  MapPin,
  PencilSimple,
  Watch,
  X,
} from "@phosphor-icons/react";

const REPO_URL = "https://github.com/xtawa/WearOS_ClassingTimeTable";

const importMethods = [
  { icon: CalendarBlank, label: "从教务系统导入", meta: ".ics" },
  { icon: BracketsCurly, label: "从文件导入", meta: ".json" },
  { icon: CloudArrowUp, label: "云端同步与备份", meta: "Google Drive / WebDAV" },
  { icon: PencilSimple, label: "手动创建", meta: "自由安排课程与时间" },
];

const reminderFeatures = [
  { icon: BellRinging, label: "课前提醒", detail: "按你的节奏提前准备" },
  { icon: MapPin, label: "教室位置", detail: "上课地点一眼确认" },
  { icon: CalendarDots, label: "整周课表", detail: "今天与本周随时切换" },
];

function Brand({ compact = false }) {
  return (
    <a className="brand" href="#top" aria-label="Classing 首页">
      <img src="/assets/classing-brand/classing-app-icon.png" alt="" />
      <span>Classing</span>
      {!compact && <small>TIMETABLE</small>}
    </a>
  );
}

function PrimaryLink({ children, className = "" }) {
  return (
    <a className={`button button-primary ${className}`} href={REPO_URL} target="_blank" rel="noreferrer">
      <span>{children}</span>
      <ArrowRight weight="bold" aria-hidden="true" />
    </a>
  );
}

function SectionRail({ number, label }) {
  return (
    <aside className="section-rail" aria-hidden="true">
      <span>SECTION</span>
      <strong>{number}</strong>
      <i />
      <small>{label}</small>
    </aside>
  );
}

function AppHeader({ open, onToggle, onNavigate }) {
  return (
    <header className="site-header">
      <Brand compact />
      <button className="menu-toggle" type="button" onClick={onToggle} aria-expanded={open} aria-controls="site-nav">
        {open ? <X weight="bold" /> : <List weight="bold" />}
        <span className="sr-only">{open ? "关闭菜单" : "打开菜单"}</span>
      </button>
      <nav id="site-nav" className={open ? "site-nav is-open" : "site-nav"} aria-label="主导航">
        <button type="button" onClick={() => onNavigate("features")}>功能</button>
        <button type="button" onClick={() => onNavigate("workflow")}>使用方式</button>
        <a href={`${REPO_URL}/issues`} target="_blank" rel="noreferrer">支持</a>
        <PrimaryLink className="header-cta">开始使用</PrimaryLink>
      </nav>
    </header>
  );
}

function Hero({ onNavigate }) {
  return (
    <section className="hero" id="top">
      <div className="hero-copy reveal">
        <p className="eyebrow"><span>COURSE 10:22</span> WEAR OS READY</p>
        <h1>别让<br /><em>下一节课，</em><br />突然出现。</h1>
        <p className="hero-lede">课程、教室、提醒与同步，<br />都在你的节奏里。</p>
        <div className="hero-actions">
          <PrimaryLink>开始使用</PrimaryLink>
          <button className="button button-text" type="button" onClick={() => onNavigate("features")}>认识 Classing <ArrowRight weight="bold" /></button>
        </div>
      </div>

      <div className="hero-visual reveal reveal-delay">
        <img src="/assets/classing-brand/hero-devices.png" alt="Classing 手机课表与 Wear OS 下一节课界面" />
        <div className="time-stamp" aria-hidden="true">
          <span>NEXT CLASS</span>
          <strong>10:45</strong>
          <small>理科楼 204</small>
        </div>
      </div>

      <div className="hero-axis" aria-hidden="true">
        <span>08:00</span><span>10:22</span><span>12:00</span><span>14:00</span><span>16:00</span>
      </div>
    </section>
  );
}

function ImportSection() {
  return (
    <section className="poster-section import-section" id="features">
      <SectionRail number="01" label="CLASSING TIMETABLE SYSTEM" />
      <div className="section-copy reveal">
        <p className="kicker">IMPORT / BUILD / RESTORE</p>
        <h2>导入你的课表</h2>
        <p className="section-intro">几步导入，自动生成属于你的每周节奏。已有课表可以直接带来，也可以从零开始。</p>
        <div className="method-list">
          {importMethods.map(({ icon: Icon, label, meta }) => (
            <div className="method-row" key={label}>
              <Icon size={26} weight="duotone" aria-hidden="true" />
              <span>{label}</span>
              <small>{meta}</small>
            </div>
          ))}
        </div>
      </div>
      <div className="import-art reveal reveal-delay">
        <img src="/assets/classing-brand/import-phone.png" alt="Classing 在 Android 手机上导入课表" />
        <div className="blueprint-copy" aria-hidden="true">
          <span>IMPORT</span>
          <strong>SCHEDULE</strong>
          <ArrowRight weight="thin" />
        </div>
      </div>
    </section>
  );
}

function SyncSection() {
  return (
    <section className="poster-section sync-section" id="workflow">
      <SectionRail number="02" label="SYNC WEAR OS" />
      <div className="sync-copy reveal">
        <p className="kicker">PHONE → WATCH</p>
        <h2>同步到 Wear OS</h2>
        <p>手机排好一周，手腕只呈现此刻最重要的那一节。课表变化后，下一节课也跟着更新。</p>
        <ul>
          <li><ArrowsClockwise weight="duotone" />手机管理，手表同步</li>
          <li><Watch weight="duotone" />抬腕即见下一节课</li>
          <li><CheckCircle weight="duotone" />离线也保留本地课表</li>
        </ul>
      </div>
      <div className="sync-visual reveal reveal-delay">
        <img src="/assets/classing-brand/wear-watch.png" alt="Classing Wear OS 下一节课提醒" />
        <div className="sync-marker" aria-hidden="true"><ArrowsClockwise weight="bold" /></div>
        <span className="sync-caption">10:22 / NEXT CLASS READY</span>
      </div>
    </section>
  );
}

function ReminderSection() {
  return (
    <section className="poster-section reminder-section">
      <SectionRail number="03" label="ON TIME EVERY DAY" />
      <div className="reminder-copy reveal">
        <p className="kicker">REMINDERS / LOCATION / WEEK</p>
        <h2>每天准时到达</h2>
        <p className="section-intro">Classing 不只是保存课表。它把课程开始前最需要的信息，放在你最顺手的位置。</p>
        <div className="reminder-list">
          {reminderFeatures.map(({ icon: Icon, label, detail }) => (
            <div className="reminder-row" key={label}>
              <Icon size={30} weight="duotone" aria-hidden="true" />
              <span><strong>{label}</strong><small>{detail}</small></span>
            </div>
          ))}
        </div>
      </div>

      <div className="notification-stage reveal reveal-delay" aria-label="下一节课通知示例">
        <div className="day-label"><span>今天</span><strong>周三</strong></div>
        <div className="notification-card is-next">
          <div><DeviceMobile weight="duotone" /><span>Classing · 10:22</span></div>
          <strong>下一节课：大学物理</strong>
          <p>10:45 开始 · 理科楼 204</p>
          <small>23 分钟后开始</small>
        </div>
        <div className="course-line">
          <span>14:00</span><strong>线性代数</strong><small>理科楼 305</small>
        </div>
        <div className="signal-seal" aria-hidden="true"><span>ON TIME</span><BellRinging weight="fill" /><small>EVERY DAY</small></div>
      </div>
    </section>
  );
}

function Footer() {
  return (
    <footer className="site-footer">
      <div>
        <Brand compact />
        <p>你的课表，由你掌控。<br />无论在手机还是手腕，Classing 都与你同步。</p>
      </div>
      <div className="footer-cta">
        <span>READY FOR THE NEXT CLASS?</span>
        <PrimaryLink>开始使用</PrimaryLink>
      </div>
      <div className="footer-meta">
        <span>ANDROID + WEAR OS</span>
        <a href={REPO_URL} target="_blank" rel="noreferrer"><GithubLogo weight="fill" /> GitHub</a>
      </div>
    </footer>
  );
}

export function App() {
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    const elements = [...document.querySelectorAll(".reveal")];
    if (reduced) {
      elements.forEach((element) => element.classList.add("is-visible"));
      return undefined;
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-visible");
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.16 });
    elements.forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, []);

  const navigateTo = (id) => {
    setMenuOpen(false);
    const target = document.getElementById(id);
    if (!target) return;
    const top = target.getBoundingClientRect().top + window.scrollY - 72;
    window.scrollTo({ top, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  };

  return (
    <div className="site-shell">
      <AppHeader open={menuOpen} onToggle={() => setMenuOpen((value) => !value)} onNavigate={navigateTo} />
      <main>
        <Hero onNavigate={navigateTo} />
        <ImportSection />
        <SyncSection />
        <ReminderSection />
      </main>
      <Footer />
    </div>
  );
}
