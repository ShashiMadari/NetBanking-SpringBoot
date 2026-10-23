#!/bin/bash
set -euo pipefail
umask 077
marker=/opt/oracle/oradata/.obank-schema-v1
if [ -f "$marker" ]; then
  echo 'O-Bank schemas already initialized; preserving existing data.'
  return 0 2>/dev/null || exit 0
fi
existing=$(sqlplus -s / as sysdba <<'SQL'
WHENEVER SQLERROR EXIT FAILURE
SET PAGESIZE 0 FEEDBACK OFF HEADING OFF VERIFY OFF ECHO OFF
ALTER SESSION SET CONTAINER=FREEPDB1;
SELECT COUNT(*) FROM dba_users WHERE username IN ('O_BANK_IDENTITY','O_BANK_BANKING','O_BANK_CREDIT','O_BANK_FOREX','O_BANK_INVESTMENT');
EXIT
SQL
)
existing=$(printf '%s' "$existing" | tr -d '[:space:]')
if [ "$existing" != '0' ]; then
  echo 'Refusing to initialize: this volume already contains O-Bank schemas without an initialization marker.' >&2
  exit 1
fi
init_sql=$(mktemp /tmp/obank-init.XXXXXX.sql)
trap 'rm -f "$init_sql"' EXIT

# Generate runtime-only SQL. Passwords are never stored in the image or logged.
python3 - "$init_sql" <<'PY'
import os, re, sys
from pathlib import Path
schemas=['IDENTITY','BANKING','CREDIT','FOREX','INVESTMENT']
lines=['WHENEVER SQLERROR EXIT SQL.SQLCODE', 'WHENEVER OSERROR EXIT FAILURE',
       'SET ECHO OFF', 'SET VERIFY OFF', 'SET DEFINE OFF', 'SET SQLBLANKLINES ON', 'SET SERVEROUTPUT ON',
       'ALTER SESSION SET CONTAINER=FREEPDB1;']
for name in schemas:
    var=name+'_DB_PASSWORD'
    password=os.environ.get(var,'')
    if not re.fullmatch(r'[A-Za-z0-9_#@$!%+=.\-]{8,64}',password):
        raise SystemExit(var+' must contain 8-64 allowed characters (letters, digits, _#@$!%+=.-)')
    user='O_BANK_'+name
    lines += [f'CREATE USER {user} IDENTIFIED BY "{password}" DEFAULT TABLESPACE USERS TEMPORARY TABLESPACE TEMP QUOTA UNLIMITED ON USERS;',
              f'GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER, CREATE VIEW, CREATE SYNONYM, CREATE TYPE TO {user};']
folder=Path('/opt/oracle/obank-schema')
for prefix in [f'{n:02d}-' for n in range(1,11)]+['12-','11-']:
    for path in sorted(folder.glob(prefix+'*.sql')):
        lines += ['PROMPT Applying '+path.name, '@'+str(path)]
lines += ['ALTER SESSION SET CURRENT_SCHEMA=SYS;',
          'BEGIN',
          "  FOR s IN (SELECT username FROM dba_users WHERE username IN ('O_BANK_IDENTITY','O_BANK_BANKING','O_BANK_CREDIT','O_BANK_FOREX','O_BANK_INVESTMENT')) LOOP",
          '    DBMS_UTILITY.COMPILE_SCHEMA(s.username, compile_all => FALSE);',
          '  END LOOP;', 'END;', '/',
          'DECLARE invalid_count NUMBER; BEGIN',
          "  SELECT COUNT(*) INTO invalid_count FROM dba_objects WHERE owner IN ('O_BANK_IDENTITY','O_BANK_BANKING','O_BANK_CREDIT','O_BANK_FOREX','O_BANK_INVESTMENT') AND status='INVALID';",
          "  IF invalid_count > 0 THEN RAISE_APPLICATION_ERROR(-20050, 'O-Bank schema has invalid objects: ' || invalid_count); END IF;",
          "  DBMS_OUTPUT.PUT_LINE('O-Bank schemas initialized successfully (structure only, no customer data).');", 'END;', '/', 'EXIT SUCCESS']
Path(sys.argv[1]).write_text('\n'.join(lines)+'\n')
PY
sqlplus -s / as sysdba @"$init_sql"
touch "$marker"
