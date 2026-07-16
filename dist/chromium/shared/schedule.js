const DAY_LABELS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

function toMinutes(time) {
  const match = /^(\d{2}):(\d{2})$/.exec(String(time ?? ""));
  if (!match) {
    return null;
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);

  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
    return null;
  }

  return hours * 60 + minutes;
}

export function isScheduleActive(schedule, now = new Date()) {
  if (!schedule || !Array.isArray(schedule.days) || !schedule.days.length) {
    return false;
  }

  const day = DAY_LABELS[now.getDay()];
  if (!schedule.days.includes(day)) {
    return false;
  }

  const start = toMinutes(schedule.startTime);
  const end = toMinutes(schedule.endTime);
  const current = now.getHours() * 60 + now.getMinutes();

  if (start === null || end === null) {
    return false;
  }

  if (start === end) {
    return true;
  }

  if (start < end) {
    return current >= start && current < end;
  }

  return current >= start || current < end;
}

export function isAnyScheduleActive(schedules = [], now = new Date()) {
  return schedules.some((schedule) => isScheduleActive(schedule, now));
}
