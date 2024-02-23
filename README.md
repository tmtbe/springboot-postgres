## 启动
```shell
docker-compose up -d
```
elasticsearch要重新生成密码，请运行：
```shell
docker exec -it projectdemo-elasticsearch-1  /usr/share/elasticsearch/bin/elasticsearch-reset-password -u elastic
```