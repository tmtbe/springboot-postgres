create table contract
(
    id                     bigint auto_increment
        primary key,
    service_name           varchar(255) not null,
    operation_id           bigint       not null,
    contract_type          varchar(255) not null comment '类型Auto还是Manual',
    created_date           datetime     not null,
    auto_request_record_id bigint       null comment '自动契约会记录record_id',
    provider_service_name  varchar(255) not null comment '契约提供者服务名',
    provider_service_image varchar(255) not null,
    consumer_service_name  varchar(255) not null,
    consumer_service_image varchar(255) not null,
    from_sut               varchar(255) not null comment '提供者属于哪个被测应用的',
    from_sut_image         varchar(255) not null comment 'sut镜像',
    `desc`                 varchar(255) not null comment '描述',
    constraint contract_auto_request_record_id_uindex
        unique (auto_request_record_id)
)
    comment 'sut的契约';

create index contract_consumer_service_image_index
    on contract (consumer_service_image);

create index contract_consumer_service_name_index
    on contract (consumer_service_name);

create index contract_from_sut_image_index
    on contract (from_sut_image);

create index contract_from_sut_index
    on contract (from_sut);

create index contract_provider_service_image_index
    on contract (provider_service_image);

create index contract_provider_service_name_index
    on contract (provider_service_name);

create table contract_record_relation
(
    id                      bigint auto_increment
        primary key,
    service_name            varchar(255) not null,
    original_service_name   varchar(255) not null comment '流量来自那个服务',
    original_service_image  varchar(255) not null,
    original_sut            varchar(255) not null comment '产生流量的被测应用是谁',
    original_sut_image      varchar(255) not null comment 'sut的镜像版本',
    contract_id             bigint       not null,
    original_record_req_id  bigint       not null,
    original_record_resp_id bigint       not null
)
    comment 'sut的契约和流量的关系';

create index contract_record_relation_original_service_name_index
    on contract_record_relation (original_service_name);

create index contract_record_relation_original_sut_image_index
    on contract_record_relation (original_sut_image);

create index contract_record_relation_original_sut_index
    on contract_record_relation (original_sut);

create index sut_contract_record_original_record_req_id_index
    on contract_record_relation (original_record_req_id);

create index sut_contract_record_original_record_resp_id_index
    on contract_record_relation (original_record_resp_id);

create table json_contract
(
    id               bigint auto_increment
        primary key,
    contract_id      bigint       not null,
    expression_path  varchar(255) not null,
    expression_value varchar(255) null,
    value_type       varchar(255) not null
);



create table open_api
(
    id           bigint auto_increment
        primary key,
    service_name varchar(255) not null,
    openapi      text         null,
    md5          varchar(128) not null,
    created_date datetime     not null
)
    comment 'sut的openapi存放';

create index original_sut_openapi_md5
    on open_api (md5);

create index original_sut_openapi_sut
    on open_api (service_name);

create table open_api_base_path
(
    id           bigint auto_increment
        primary key,
    open_api_id  bigint       not null,
    base_path    varchar(255) not null,
    service_name varchar(255) not null
);

create index openapi_base_path_openapi_id_index
    on open_api_base_path (open_api_id);

create index openapi_base_path_service_name_index
    on open_api_base_path (service_name);

create table open_api_md5_operation_relation
(
    id           bigint auto_increment
        primary key,
    service_name varchar(255) not null,
    openapi_md5  varchar(128) not null,
    operation_id bigint       not null
)
    comment 'sut的openapi的操作分级与不同版本openapi的关系';

create index original_sut_openapi_md5_operation_relation_openapi_md5
    on open_api_md5_operation_relation (openapi_md5);

create index original_sut_openapi_md5_operation_relation_operation_id
    on open_api_md5_operation_relation (operation_id);

create index original_sut_openapi_md5_operation_relation_sut
    on open_api_md5_operation_relation (service_name);

create table open_api_operation
(
    id           bigint auto_increment
        primary key,
    service_name varchar(255) not null,
    path         varchar(255) not null,
    method       varchar(10)  not null,
    code         int          not null
)
    comment 'sut的openapi的操作分级';

create index original_sut_openapi_operation_code
    on open_api_operation (code);

create index original_sut_openapi_operation_method
    on open_api_operation (method);

create index original_sut_openapi_operation_path
    on open_api_operation (path);

create index original_sut_openapi_operation_sut
    on open_api_operation (service_name);

create table original_record
(
    id             bigint auto_increment
        primary key,
    sut            varchar(255) not null,
    batch_id       varchar(36)  not null,
    sut_image      varchar(255) not null,
    service_name   varchar(255) not null,
    service_image  varchar(255) not null,
    trace_id       varchar(255) not null,
    span_id        varchar(255) not null,
    parent_span_id varchar(255) not null,
    trace_type     varchar(10)  not null,
    trace_kind     varchar(10)  not null,
    created_date   datetime     not null
)
    comment '原始流量记录';

create index original_record_batch_id
    on original_record (batch_id);

create index original_record_image
    on original_record (sut_image);

create index original_record_kind
    on original_record (trace_kind);

create index original_record_parent_span_id
    on original_record (parent_span_id);

create index original_record_service_image_index
    on original_record (service_image);

create index original_record_service_name
    on original_record (service_name);

create index original_record_span_id
    on original_record (span_id);

create index original_record_sut
    on original_record (sut);

create index original_record_trace_id
    on original_record (trace_id);

create index original_record_type
    on original_record (trace_type);

create table original_record_body
(
    id         bigint auto_increment
        primary key,
    record_id  bigint       not null,
    body_type  varchar(255) not null,
    body_value text         null
);

create index original_record_body_body_type
    on original_record_body (body_type);

create index original_record_body_record_id
    on original_record_body (record_id);

create table original_record_header
(
    id           bigint auto_increment
        primary key,
    record_id    bigint       not null,
    header_key   varchar(255) not null,
    header_value varchar(255) null
);

create index original_record_header_key
    on original_record_header (header_key);

create index original_record_header_record_id
    on original_record_header (record_id);

create table service
(
    id           bigint auto_increment
        primary key,
    service_name varchar(255) not null,
    constraint service_service_name_uindex
        unique (service_name)
);

create table contract_openapi_validator
(
    id          bigint auto_increment
        primary key,
    contract_id bigint     not null,
    open_api_id bigint     not null,
    success     tinyint(1) not null,
    results     text       null
);

create index contract_openapi_validator_contract_id_index
    on contract_openapi_validator (contract_id);

create index contract_openapi_validator_open_api_id_index
    on contract_openapi_validator (open_api_id);

create index contract_openapi_validator_success_index
    on contract_openapi_validator (success);

create table original_record_openapi_validator
(
    id                          bigint auto_increment
        primary key,
    open_api_id                 bigint     not null,
    original_record_request_id  bigint     not null,
    original_record_response_id bigint     not null,
    success                     tinyint(1) not null,
    results                     text       null
);

create index original_record_openapi_validator_open_api_id_index
    on original_record_openapi_validator (open_api_id);

create index original_record_openapi_validator_original_record_req_id_index
    on original_record_openapi_validator (original_record_request_id);

create index original_record_openapi_validator_original_record_resp_id_index
    on original_record_openapi_validator (original_record_response_id);

create index original_record_openapi_validator_success_index
    on original_record_openapi_validator (success);

create table original_record_operation_relation
(
    id                          bigint auto_increment
        primary key,
    original_record_request_id  bigint not null,
    original_record_response_id bigint not null,
    operation_id                bigint not null
);

create index original_record_operation_relation_operation_id_index
    on original_record_operation_relation (operation_id);

create index original_record_operation_relation_original_record_req_id_index
    on original_record_operation_relation (original_record_request_id);

create index original_record_operation_relation_original_record_resp_id_index
    on original_record_operation_relation (original_record_response_id);


