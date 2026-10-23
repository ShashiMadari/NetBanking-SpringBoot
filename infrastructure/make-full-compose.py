"""Regenerate the published full-stack deployment from the mirrored application config."""
from pathlib import Path
import yaml

root=Path(__file__).resolve().parent.parent
base=yaml.safe_load((root/'compose.mirrored.yaml').read_text())
dbnames=['IDENTITY','BANKING','CREDIT','FOREX','INVESTMENT']
oracle_env={'ORACLE_PWD':'${ORACLE_PWD:?required}'}
oracle_env.update({n+'_DB_PASSWORD':'${'+n+'_DB_PASSWORD:?required}' for n in dbnames})
oracle={
 'image':'${IMAGE_REGISTRY:-quay.io/shashimadari}/o-bank-oracle:${IMAGE_TAG:-demo-20261023}',
 'restart':'unless-stopped', 'shm_size':'1g',
 'ports':['127.0.0.1:${ORACLE_HOST_PORT:-1522}:1521'],
 'environment':oracle_env, 'volumes':['oracle-data:/opt/oracle/oradata'],
 'healthcheck':{'test':['CMD-SHELL','test -f /opt/oracle/oradata/.obank-schema-v1 && /opt/oracle/checkDBStatus.sh >/dev/null 2>&1'],
                'interval':'15s','timeout':'10s','retries':40,'start_period':'90s'}
}
kafka={
 'image':'${IMAGE_REGISTRY:-quay.io/shashimadari}/o-bank-kafka:${IMAGE_TAG:-demo-20261023}',
 'restart':'unless-stopped','network_mode':'host',
 'volumes':['kafka-data:/var/lib/kafka/data'],
 'healthcheck':{'test':['CMD','/opt/kafka/bin/kafka-broker-api-versions.sh','--bootstrap-server','localhost:9092'],
                'interval':'10s','timeout':'10s','retries':20,'start_period':'30s'}
}
services={'oracle':oracle,'kafka':kafka}
for name, service in base['services'].items():
 service['image']=service['image'].replace('${IMAGE_REGISTRY:-localhost}','${IMAGE_REGISTRY:-quay.io/shashimadari}').replace('${IMAGE_TAG:-dev}','${IMAGE_TAG:-demo-20261023}')
 env=service.get('environment',{})
 if 'SPRING_DATASOURCE_URL' in env:
  env['SPRING_DATASOURCE_URL']='jdbc:oracle:thin:@//localhost:${ORACLE_HOST_PORT:-1522}/FREEPDB1'
  deps={n:{'condition':'service_started'} for n in service.get('depends_on',[])}
  deps.update({'oracle':{'condition':'service_healthy'},'kafka':{'condition':'service_healthy'}})
  service['depends_on']=deps
 services[name]=service
full={'name':'o-bank-full','services':services,'volumes':{'oracle-data':{},'kafka-data':{}}}
(root/'compose.full.yaml').write_text('# Published Windows/WSL mirrored stack, including clean Oracle and Kafka.\n'+yaml.safe_dump(full,sort_keys=False),encoding='utf-8')
print('Generated compose.full.yaml with',len(services),'services')
