# Deploy — API Gateway

Variables de entorno: ver `.env.example` (incluye cuáles son obligatorias, cuáles van a
Secret Manager y los defaults).

## Tabla de rutas

| id | Path | Destino (`*_SERVICE_URL`) | Rewrite `/api` | Filtros |
|---|---|---|---|---|
| `user-register` | `/api/user/register` | `USER_SERVICE_URL` | Sí | RateLimit (público) + CircuitBreaker |
| `user-auth` | `/api/auth/**` | `USER_SERVICE_URL` | Sí | RateLimit (público) + CircuitBreaker |
| `user-service` | `/api/user/**` | `USER_SERVICE_URL` | Sí | JWT + RateLimit + CircuitBreaker |
| `pet-service` | `/api/pets/**` | `PET_SERVICE_URL` | Sí | JWT + RateLimit + CircuitBreaker |
| `veterinary-service` | `/api/veterinary/**` | `VETERINARY_SERVICE_URL` | **No** | JWT + RateLimit + CircuitBreaker |
| `medical-pet-service` | `/api/visits/**`, `/api/weight/**`, `/api/treatments/**`, `/api/lab/**`, `/api/medications/**`, `/api/surgery/**`, `/api/vaccines/**`, `/api/allergies/**`, `/api/dewormings/**`, `/api/documents/**` | `MEDICAL_SERVICE_URL` | No | JWT + RateLimit + CircuitBreaker |
| `calendar-service` | `/api/calendar-events/**`, `/api/veterinaries/**`, `/api/me/appointments/**` | `CALENDAR_SERVICE_URL` (solo host, trim aplicado) | **No** | JWT + RateLimit + CircuitBreaker |
| `notification-service` | `/api/notifications/**` | `NOTIFICATION_SERVICE_URL` | Sí | JWT + RateLimit + CircuitBreaker |
| `ia-service` | `/api/ai/**` | `IA_SERVICE_URL` | **No** | JWT + RateLimit (1/3, no 20/40) + CircuitBreaker (umbral 70%) |

No enrutados a propósito (deben seguir sin ruta): `/api/ai/internal/**` y
`/internal/jobs/appointments/reminders` (Cloud Scheduler) de Calendar.

## CORS

Una sola configuración (`config/CorsConfig.java`, bean `CorsWebFilter`):

- `CORS_ALLOWED_ORIGINS` — lista separada por coma, cada origen con `trim()`, nunca `*`.
- Métodos: GET, POST, PATCH, PUT, DELETE, OPTIONS.
- Headers permitidos: `Authorization`, `Content-Type`, `Accept`.
- `allowCredentials=false` (se usa Bearer, no cookies).
- `maxAge=3600`.
- `DedupeResponseHeader` (`RETAIN_FIRST`) sobre `Access-Control-Allow-Origin` y
  `Access-Control-Allow-Credentials`, por si el servicio destino manda los suyos.

## Variables nuevas en este cambio

- `IA_SERVICE_URL`
- `CORS_ALLOWED_ORIGINS` (reemplaza a `FRONTEND_URLS`/`FRONTEND_URL`, que ya no se usan)
