// SPDX-License-Identifier: AGPL-3.0-only
package com.lyra.blocker;

public final class HandoffChecks {
    public static void main(String[]args){run();System.out.println("PWA scope, source hint and repository boundary checks passed");}
    public static void run(){
        check(HandoffPolicy.inScope("https://school.test/chat?m57_intent=new","https://school.test/", "https://school.test/"),"Root PWA missed");
        check(HandoffPolicy.inScope("https://school.test:443/chat/new","https://school.test/chat/","https://school.test/chat"),"HTTPS default port mismatch");
        check(HandoffPolicy.inScope("https://school.test","https://school.test/",null),"Empty path root missed");
        check(!HandoffPolicy.inScope("https://school.test/chatty","https://school.test/chat/","https://school.test/chat"),"Scope path boundary failed");
        check(!HandoffPolicy.inScope("https://other-school.test/chat","https://school.test/",null),"Wrong school PWA accepted");
        check(!HandoffPolicy.inScope("https://school.test/chat/%2e%2e/admin","https://school.test/chat/",null),"Ambiguous path accepted");
        check(!HandoffPolicy.inScope("https://school.test/chat","https://school.test/","https://evil.test/"),"Cross-origin scope accepted");
        check(HandoffPolicy.repositoryAddress("github.com/akshay-abraham/m57/issues"),"Repository guard missed");
        check(HandoffPolicy.repositoryAddress("https://raw.githubusercontent.com/akshay-abraham/m57/main/README.md"),"Raw repo missed");
        check(!HandoffPolicy.repositoryAddress("https://github.com/akshay-abraham/m570"),"Other repository blocked");
        check(!HandoffPolicy.repositoryAddress("https://github.com.evil.test/akshay-abraham/m57"),"Lookalike blocked");
        check(!HandoffPolicy.repositoryAddress("https://github.com/another/m57"),"Other owner's repo blocked");
        check("chatgpt".equals(HandoffPolicy.source("chat.openai.com")),"ChatGPT hint missed");
        check("other".equals(HandoffPolicy.source("chatgpt.com.evil.test")),"Lookalike source matched");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
