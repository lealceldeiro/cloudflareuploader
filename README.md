# Cloudflare uploader
SpringBoot app to load a video to Cloudflare by using Cloudflare Stream feature.

## Run the app

1) Checkout the GitHub project:
```
git clone https://github.com/lealceldeiro/cloudflareuploader.git
```

2) Run the following command from the project root directory. Make sure you **replace `<token>` and `<account_id>`**
with your Cloudflare [API Token](https://developers.cloudflare.com/fundamentals/api/get-started/create-token/) and
[Account ID](https://developers.cloudflare.com/fundamentals/get-started/basic-tasks/find-account-and-zone-ids/)
values. Optionally, you can provide another argument, `--public-video-url=<full_url>`, to specify the video to upload.
By default, this one will be used: `https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_1MB.mp4`.
You can find some other examples [here](https://test-videos.co.uk/bigbuckbunny/mp4-h264)

```shell
./mvnw spring-boot:run -Dspring-boot.run.arguments="--cloudflare.account-token=<token> --cloudflare.account-id=<account_id> --public-video-url=<video_url>"
```

Example:

```shell
./mvnw spring-boot:run -Dspring-boot.run.arguments="--cloudflare.account-token=xyzToken --cloudflare.account-id=abc --public-video-url=https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/1080/Big_Buck_Bunny_1080_10s_1MB.mp4"
```

---

You'll see in the logs, the following error message (along with other info), produced by the
[tus-java-client](https://github.com/tus/tus-java-client), referenced in the
[Cloudflare docs](https://developers.cloudflare.com/stream/uploading-videos/upload-video-file/#what-is-tus).

This comes from the tus-java client used in [UploadService](https://github.com/lealceldeiro/cloudflareuploader/blob/main/src/main/java/cloudflare/uploader/video/UploadService.java#L64)

```text
unexpected status code (400) while creating upload
```
