export type TaskPhase = 'due' | 'opens' | 'closes';
export type TaskType = 'assignment' | 'forum' | 'exam' | 'other';

export interface Task {
  id: string;          // estable entre ejecuciones (clave para Room en Android)
  type: TaskType;
  phase: TaskPhase | null; // due = vence, opens = se abre, closes = se cierra
  title: string;
  course: string | null;
  dueAt: string | null; // ISO-8601 con offset, ej. 2026-11-14T23:59:00-04:00
  dueAtRaw: string;     // texto original del portal (útil para depurar)
  url: string | null;
}

export interface TasksResponse {
  ok: boolean;
  generatedAt: string;
  count: number;
  tasks: Task[];
}
