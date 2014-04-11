package sprouch.docLogger

import spray.http.HttpRequest

class JavaGenerator extends CodeGenerator {
  override def generateCode(request:HttpRequest):Seq[String] = {
    val url = request.uri.toString
    Seq(
    """String url = """ + doubleQuoted(url) + ";",
    """DefaultHttpClient httpClient = new DefaultHttpClient();""",
    /*HttpPost httpPost = new HttpPost(baseUrl);
    final HttpEntity entity = new StringEntity("{ \"name\": \"john\", \"age\": 35 }", ContentType.APPLICATION_JSON);
    httpPost.setEntity(entity);
    addAuth(httpPost);
    final HttpResponse postResp = httpClient.execute(httpPost);
    final InputStream is = postResp.getEntity().getContent();
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode postRespDoc = mapper.readValue(is, ObjectNode.class);
    final String id = ((TextNode)postRespDoc.get("id")).getTextValue();
    System.out.println("The new document's ID is " + id + ".");
    httpPost.releaseConnection();*/
    """HttpGet httpGet = new HttpGet(url);""",
    """String encodedCreds = new String(Base64.encodeBase64((user + ":" + pass).getBytes()));""",
    """httpGet.setHeader("Authorization", "Basic " + encodedCreds);""",
    """HttpResponse getResp = httpClient.execute(httpGet);"""
    )
  }
}
