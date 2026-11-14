export const dayNumber = date => Math.floor(Date.parse(`${date}T00:00:00Z`) / 86400000);
export const weekday = date => (new Date(`${date}T00:00:00Z`).getUTCDay() + 6) % 7 + 1;
export const addDays = (date, offset) => new Date((dayNumber(date) + offset) * 86400000).toISOString().slice(0,10);
export const weekStart = (date, start=1) => addDays(date, -((weekday(date) - start + 7) % 7));
export const clock = minute => `${String(Math.floor(minute/60)).padStart(2,'0')}:${String(minute%60).padStart(2,'0')}`;
export function matchesRule(rule, date, fallback) {
  if (date < rule.anchorDate || date > rule.endDate || (rule.holidays || []).includes(date)) return false;
  if (rule.kind === 'DATES') return (rule.dates || []).includes(date);
  if (rule.kind === 'WEEKLY') return (rule.daysOfWeek?.length ? rule.daysOfWeek : [fallback]).includes(weekday(date)) && (dayNumber(weekStart(date,rule.weekStartDay||1)) - dayNumber(weekStart(rule.anchorDate,rule.weekStartDay||1))) / 7 % (rule.interval||1) === 0;
  const school = rule.schoolDays || [1,2,3,4,5]; if (!school.includes(weekday(date))) return false;
  let elapsed = 0; for (let d=rule.anchorDate; d<date; d=addDays(d,1)) if (school.includes(weekday(d)) && (rule.advanceOnHolidays || !(rule.holidays||[]).includes(d))) elapsed++;
  return (rule.cycleDays || []).includes(elapsed % rule.cycleLength + 1);
}
export function weekIndex(date, settings) {
  const names=['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'];
  const start=Math.max(1,names.indexOf(settings.weekStartDay)+1);
  if(settings.weekNumberMode !== 'NATURAL')return Math.floor((dayNumber(weekStart(date,start))-dayNumber(weekStart(settings.semesterWeekStartDate,start)))/7)+1;
  const year=Number(date.slice(0,4)), first=weekStart(`${year}-01-04`,start), next=weekStart(`${year+1}-01-04`,start);
  if(date>=next)return 1;
  if(date<first)return Math.floor((dayNumber(weekStart(date,start))-dayNumber(weekStart(`${year-1}-01-04`,start)))/7)+1;
  return Math.floor((dayNumber(weekStart(date,start))-dayNumber(first))/7)+1;
}
export function project(state, date) {
  const settings = state.settings.values, week = weekIndex(date,settings);
  const changes = (state.exceptions || []).filter(e => e.date === date), output=[];
  if (!changes.some(e => e.type === 'HOLIDAY' && !e.lessonId)) for (const lesson of state.lessons || []) {
    const matches = lesson.scheduleRuleJson ? matchesRule(JSON.parse(lesson.scheduleRuleJson),date,lesson.dayOfWeek) : lesson.dayOfWeek === weekday(date) && week >= lesson.startWeek && week <= lesson.endWeek && (lesson.weekParity === 'ALL' || (lesson.weekParity === 'ODD' ? week%2===1 : week%2===0));
    if (!matches) continue;
    const edits = changes.filter(e => e.lessonId === lesson.id); if (edits.some(e => ['CANCEL','HOLIDAY'].includes(e.type))) continue;
    const moves = edits.filter(e => e.type === 'RESCHEDULE');
    if (!moves.length) output.push({...lesson,id:`${lesson.id}:${date}`,lessonId:lesson.id,date,source:'REGULAR'});
  }
  for(const e of changes.filter(e=>e.type==='RESCHEDULE')) {
    if(changes.some(c=>c.lessonId===e.lessonId&&['CANCEL','HOLIDAY'].includes(c.type)))continue;
    const l=state.lessons.find(l=>l.id===e.lessonId);if(!l)continue;
    output.push({...l,...e,title:l.title,id:e.id,exceptionId:e.id,source:l.dayOfWeek===weekday(date)&&l.startMinute===e.startMinute&&l.endMinute===e.endMinute?'REGULAR':'RESCHEDULED',date});
  }
  for (const e of changes.filter(e=>e.type==='MAKE_UP')) output.push({...e,source:'MAKE_UP'});
  return output.sort((a,b)=>a.startMinute-b.startMinute || a.id.localeCompare(b.id));
}
export function conflicts(items) { const pairs=[]; items.forEach((a,i)=>items.slice(i+1).forEach(b=>{if(a.startMinute<b.endMinute&&b.startMinute<a.endMinute)pairs.push([a,b]);})); return pairs; }
export function validateLesson(l) {
  if (!l.title?.trim() || l.title.length>1000 || !Number.isInteger(l.dayOfWeek) || l.dayOfWeek<1 || l.dayOfWeek>7 || !Number.isInteger(l.startMinute) || !Number.isInteger(l.endMinute) || l.startMinute<0 || l.endMinute>1440 || l.endMinute<=l.startMinute || l.startWeek<1 || l.endWeek<l.startWeek || l.endWeek>53 || !['ALL','ODD','EVEN'].includes(l.weekParity)) throw Error('请检查课程名称、时间、星期和周次。'); return l;
}
export function parseDraft(raw) {
  if(raw.trim().startsWith('{')||raw.trim().startsWith('[')) { const data=JSON.parse(raw), rows=Array.isArray(data)?data:(data.lessons||data.timetable?.lessons||[]); if(!rows.length)throw Error('未找到 lessons 数组。'); return rows.map((l,i)=>validateLesson({...l,id:l.id||`preview-import-${i}`,startMinute:l.startMinute??parseClock(l.startTime),endMinute:l.endMinute??parseClock(l.endTime),startWeek:l.startWeek??1,endWeek:l.endWeek??20,weekParity:l.weekParity||'ALL'})); }
  return raw.trim().split(/\r?\n/).map((line,i)=>{const p=line.split('\t');return validateLesson({id:`preview-import-${i}`,title:p[0],dayOfWeek:Number(p[1]),startMinute:parseClock(p[2]),endMinute:parseClock(p[3]),location:p[4]||'',startWeek:1,endWeek:20,weekParity:'ALL'});});
}
export function parseClock(value) { if(!/^\d{2}:\d{2}$/.test(value||''))throw Error('时间应为 HH:mm。'); const [h,m]=value.split(':').map(Number); if(h>23||m>59)throw Error('无效时间。'); return h*60+m; }
