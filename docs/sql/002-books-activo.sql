-- Cambio aditivo requerido por GDS2026-102 (estado del libro).
-- Aplicar explícitamente antes de activar esa versión. No se ejecuta al arrancar.
-- Los libros existentes quedan activos; no se borra ni se recrea ninguna tabla.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE public.books
    ADD COLUMN IF NOT EXISTS activo boolean NOT NULL DEFAULT true;
COMMIT;
