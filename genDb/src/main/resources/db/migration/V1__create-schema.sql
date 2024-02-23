create table public.index
(
    id        bigserial
        primary key,
    name      varchar(255)              not null,
    es_index  varchar(255),
    "desc"    varchar(255),
    create_at timestamp default current_timestamp,
    update_at timestamp
);

alter table public.index
    owner to test;

create index index_create_at_index
    on public.index (create_at);

create index index_es_index_index
    on public.index (es_index);

create index index_name_index
    on public.index (name);

create index index_update_at_index
    on public.index (update_at);

create table public.property
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
    create_at   timestamp default current_timestamp,
    update_at   timestamp
);

comment on column public.property.doc_id_part is '是否作为文档id构建的一部分';

comment on column public.property.restrict is '约束条件';

alter table public.property
    owner to test;

create index property_create_at_index
    on public.property (create_at);

create index property_doc_id_part_index
    on public.property (doc_id_part);

create index property_index_id_index
    on public.property (index_id);

create index property_name_index
    on public.property (name);

create index property_type_index
    on public.property (type);

create index property_update_at_index
    on public.property (update_at);

create table public.doc
(
    id        bigserial
        primary key,
    index_id  bigint                         not null,
    batch_id  varchar(255) default 'default' not null,
    source    text,
    create_at timestamp default current_timestamp
);

comment on column public.doc.batch_id is '用于区分文档上传时候的批次信息';

alter table public.doc
    owner to test;

create index doc_batch_id_index
    on public.doc (batch_id);

create index doc_create_at_index
    on public.doc (create_at);

create index doc_index_id_index
    on public.doc (index_id);

-- doc创建自动分区触发器
CREATE OR REPLACE FUNCTION create_partition_and_insert()
    RETURNS TRIGGER AS $$
BEGIN
    -- 确认分区表是否存在
    IF NOT EXISTS(SELECT * FROM pg_class WHERE relname = 'doc_index_' || NEW.index_id AND relkind = 'r') THEN
        EXECUTE 'CREATE TABLE doc_index_' || NEW.index_id || ' (CHECK (index_id = ''' || NEW.index_id || ''')) INHERITS (public.doc)';
        -- 创建索引
        EXECUTE 'CREATE INDEX idx_doc_index_' || NEW.index_id || '_batch_id ON doc_index_' || NEW.index_id || '(batch_id)';
        EXECUTE 'CREATE INDEX idx_doc_index_' || NEW.index_id || '_create_at ON doc_index_' || NEW.index_id || '(create_at)';
        EXECUTE 'CREATE INDEX idx_doc_index_' || NEW.index_id || '_index_id ON doc_index_' || NEW.index_id || '(index_id)';
    END IF;

    -- 插入数据到相应的分区表
    EXECUTE 'INSERT INTO doc_index_' || NEW.index_id || ' (index_id, batch_id, source, create_at)
             VALUES ($1.index_id, $1.batch_id, $1.source, COALESCE($1.create_at, CURRENT_TIMESTAMP))' USING NEW;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER insert_doc_trigger
    BEFORE INSERT ON public.doc
    FOR EACH ROW
EXECUTE FUNCTION create_partition_and_insert();

-- 创建update_at触发器
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.update_at = now();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_table_name_update_at BEFORE UPDATE ON public.index FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();
CREATE TRIGGER update_table_name_update_at BEFORE UPDATE ON public.property FOR EACH ROW EXECUTE PROCEDURE  update_modified_column();