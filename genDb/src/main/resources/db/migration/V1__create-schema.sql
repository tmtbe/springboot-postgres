create table public.collection
(
    id        bigserial
        primary key,
    name      varchar(255)              not null,
    "desc"    varchar(255),
    create_at timestamp default current_timestamp,
    update_at timestamp
);

create index collection_create_at_index
    on public.collection (create_at);

create unique index collection_index_name_index
    on public.collection (name);

create index collection_index_update_at_index
    on public.collection (update_at);

create table public.collection_property
(
    id            bigserial
        primary key,
    collection_id bigint       not null,
    name          varchar(255) not null,
    type          varchar(255) not null,
    create_at     timestamp default current_timestamp
);

create index collection_property_collection_id_index
    on public.collection_property (collection_id);

create index collection_property_create_at_index
    on public.collection_property (create_at);

create type index_status as enum ('Inactivated','Activated','Migrating');
create table public.index
(
    id        bigserial
        primary key,
    name      varchar(255)              not null,
    collection_id      bigint              not null,
    collection_name    varchar(255)        not null,
    es_index_name  varchar(255),
    "desc"    varchar(255),
    status    index_status default 'Inactivated' not null,
    auto_append_from_collection boolean not null default false,
    create_at timestamp default current_timestamp,
    update_at timestamp
);


create index index_collection_id_index
    on public.index (collection_id);

create index index_create_at_index
    on public.index (create_at);

create index index_es_index_name_index
    on public.index (es_index_name);

create unique index index_name_index
    on public.index (name);

create index index_update_at_index
    on public.index (update_at);

create table public.index_property
(
    id          bigserial
        primary key,
    index_id    bigint                       not null,
    name        varchar(255)                 not null,
    alias       varchar(255),
    type        varchar(255)                 not null,
    required    boolean default false,
    doc_id_part boolean default false,
    restrict    varchar(1024),
    enable boolean default true,
    create_at   timestamp default current_timestamp,
    update_at   timestamp
);

comment on column public.index_property.doc_id_part is '是否作为文档id构建的一部分';

comment on column public.index_property.restrict is '约束条件';


create index index_property_create_at_index
    on public.index_property (create_at);

create index index_property_doc_id_part_index
    on public.index_property (doc_id_part);

create index index_property_index_id_index
    on public.index_property (index_id);

create index index_property_name_index
    on public.index_property (name);

create index index_property_type_index
    on public.index_property (type);

create index index_property_update_at_index
    on public.index_property (update_at);

create index index_property_enable_index
    on public.index_property (enable);

create table public.index_doc_record
(
    id        bigserial
        primary key,
    index_id  bigint                    not null,
    latest_doc_id    bigint                    not null,
    create_at timestamp default current_timestamp
);

create index index_doc_record_latest_doc_id_index
    on public.index_doc_record (latest_doc_id);

create index index_doc_record_index_id_index
    on public.index_doc_record (index_id);

create table public.doc
(
    id        bigserial
        primary key,
    collection_id  bigint                    not null,
    batch_id  varchar(255) default 'default' not null,
    source    text,
    modify_by_index bigint,
    create_at timestamp default current_timestamp
);

comment on column public.doc.batch_id is '用于区分文档上传时候的批次信息';


create index doc_batch_id_index
    on public.doc (batch_id);

create index doc_create_at_index
    on public.doc (create_at);

create index doc_collection_id_index
    on public.doc (collection_id);

create index doc_modify_by_index_index
    on public.doc (modify_by_index);

-- doc创建自动分区触发器
CREATE OR REPLACE FUNCTION create_partition_and_insert()
    RETURNS TRIGGER AS $$
BEGIN
    -- 确认分区表是否存在
    IF NOT EXISTS(SELECT * FROM pg_class WHERE relname = 'doc_collection_' || NEW.collection_id AND relkind = 'r') THEN
        EXECUTE 'CREATE TABLE doc_collection_' || NEW.collection_id || ' (CHECK (collection_id = ''' || NEW.collection_id || ''')) INHERITS (public.doc)';
        -- 创建索引
        EXECUTE 'CREATE INDEX idx_doc_collection_' || NEW.collection_id || '_batch_id ON doc_collection_' || NEW.collection_id || '(batch_id)';
        EXECUTE 'CREATE INDEX idx_doc_collection_' || NEW.collection_id || '_create_at ON doc_collection_' || NEW.collection_id || '(create_at)';
        EXECUTE 'CREATE INDEX idx_doc_collection_' || NEW.collection_id || '_collection_id ON doc_collection_' || NEW.collection_id || '(collection_id)';
        EXECUTE 'CREATE INDEX idx_doc_collection_' || NEW.collection_id || '_modify_by_index ON doc_collection_' || NEW.collection_id || '(modify_by_index)';
    END IF;

    -- 插入数据到相应的分区表
    EXECUTE 'INSERT INTO doc_collection_' || NEW.collection_id || ' (collection_id, batch_id, source, create_at)
             VALUES ($1.collection_id, $1.batch_id, $1.source, COALESCE($1.create_at, CURRENT_TIMESTAMP))' USING NEW;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER insert_doc_trigger
    BEFORE INSERT ON public.doc
    FOR EACH ROW
EXECUTE FUNCTION create_partition_and_insert();

create type job_status as enum ('Created','Running','Succeed','Failed');
create type job_type as enum ('IndexMigrate');
create table public.job
(
    id        bigserial
        primary key,
    job_type  job_type   not null,
    job_data  jsonb,
    status    job_status not null,
    create_at timestamp default current_timestamp,
    update_at timestamp
);
create index job_job_type_index
    on public.job (job_type);
create index job_status_index
    on public.job (status);
create index job_create_at_index
    on public.job (create_at);
create index job_update_at_index
    on public.job (update_at);

create type log_type as enum ('Info','Error');
create table public.job_log
(
    id        bigserial
        primary key,
    job_id    bigint   not null,
    log_type  log_type not null,
    log       text,
    create_at timestamp default current_timestamp
);
create index job_log_job_id_index
    on public.job_log (job_id);
create index job_log_log_type_index
    on public.job_log (log_type);
create index job_log_create_at_index
    on public.job_log (create_at);

-- 创建update_at触发器
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.update_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_table_name_update_at BEFORE UPDATE ON public.collection FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();
CREATE TRIGGER update_table_name_update_at BEFORE UPDATE ON public.index FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();
CREATE TRIGGER update_table_name_update_at BEFORE UPDATE ON public.index_property FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();
CREATE TRIGGER update_table_name_update_at
    BEFORE UPDATE
    ON public.job
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();