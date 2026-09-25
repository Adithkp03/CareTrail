export type MilestoneStatus = "done" | "now" | "upcoming" | "next";

export type Milestone = {
  id: string;
  key: string;
  title: string;
  type: "visit" | "scan" | "test" | "vaccination" | "review";
  window_weeks: [number, number];
  required_tests: string[];
  prep_notes: string;
  status: MilestoneStatus;
  overdue: boolean;
  completed_at: string | null;
  scheduled_date: string | null;
  signoff: { doctor_name: string; note: string; signed_at: string; verified_clinician?: boolean } | null;
  documents?: { id: string; filename: string; status: string; language: string | null }[];
  observations?: { id: string; code: string; value: number; unit: string; observed_on: string }[];
};

export type Journey = {
  journey_id: string;
  template_id: string;
  template_version: string;
  patient: { name: string; language: string };
  gestational_age: { days: number; weeks: number; plus_days: number; as_of: string };
  lmp: string | null;
  edd: string;
  summary: Record<MilestoneStatus, number>;
  next_appointment: { key: string; title: string; scheduled_date: string } | null;
  milestones: Milestone[];
  flags: Flag[];
  danger_signs: string[];
};

export type Flag = {
  code: string;
  label: string;
  value: number;
  unit: string;
  observed_on: string;
  observation_id: string;
  threshold: { min?: number; max?: number };
  message: string;
  severity: string;
  signed_off: { doctor_name: string; note: string; signed_at: string; verified_clinician?: boolean } | null;
};

export type NextUp = {
  milestone: { id: string; key: string; title: string; type: Milestone["type"]; window_weeks: [number, number]; scheduled_date: string | null } | null;
  purpose?: string;
  what_to_bring?: string;
  fasting?: string | null;
  questions?: string[];
  message?: string;
};

export type Brief = {
  patient: { name: string; language: string };
  gestational_age: { weeks: number; plus_days: number; as_of: string };
  edd: string;
  open_flags: Flag[];
  signed_flags: Flag[];
  latest_values: { code: string; value: number; unit: string; observed_on: string }[];
  next_appointment: { title: string; scheduled_date: string } | null;
  recent_reports: { filename: string; status: string; uploaded_at: string }[];
};
