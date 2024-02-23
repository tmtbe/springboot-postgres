## 启动
```shell
docker-compose up -d
```
elasticsearch要重新生成密码，请运行：
```shell
docker exec -it projectdemo-elasticsearch-1  /usr/share/elasticsearch/bin/elasticsearch-reset-password -u elastic
```


Collection 存所有的document，往这里导入原始数据
  1：1 Index 将Collection数据按索引规则导入到Index中，这里的数据必须包含collection doc的id引用，Index有数据格式和规则进行数据清洗，对Index的数据进行修改拉取原始数据合并并插入Collection一条新的数据
    1：N Views Indices的查询视图（只读）