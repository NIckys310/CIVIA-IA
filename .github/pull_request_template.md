## Qué cambia y por qué

## Cómo probarlo

## Checklist
- [ ] Tests en verde (`pytest`, `turbo run typecheck test`)
- [ ] Contrato OpenAPI regenerado si cambió la API
- [ ] Sin secretos, `.env` ni datos de clientes en el diff
- [ ] Autorización revisada: ¿un usuario de otra organización puede acceder a esto?
- [ ] Textos de la interfaz en lenguaje prudente ("posible inconsistencia", nunca "es seguro/inseguro")
- [ ] Docs/ADR actualizados si hay una decisión nueva
